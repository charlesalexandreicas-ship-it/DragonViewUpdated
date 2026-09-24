-- Shared farm workspaces: one active workspace per account, with OWNER and FARMER roles.
create table public.workspaces (
    id uuid primary key default extensions.gen_random_uuid(),
    name text not null check (length(trim(name)) between 2 and 80),
    owner_id uuid not null references public.profiles(id) on delete cascade,
    invite_code text not null unique check (invite_code ~ '^[A-Z0-9]{8}$'),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    version bigint not null default 1
);

create table public.workspace_members (
    workspace_id uuid not null references public.workspaces(id) on delete cascade,
    user_id uuid not null references public.profiles(id) on delete cascade,
    role text not null check (role in ('OWNER','FARMER')),
    joined_at timestamptz not null default now(),
    primary key (workspace_id,user_id)
);

alter table public.profiles add column active_workspace_id uuid references public.workspaces(id) on delete set null;

create or replace function public.new_workspace_invite_code()
returns text language plpgsql volatile security definer set search_path=public,pg_temp as $$
declare candidate text;
begin
  loop
    candidate:=upper(substr(encode(extensions.gen_random_bytes(8),'hex'),1,8));
    exit when not exists(select 1 from public.workspaces where invite_code=candidate);
  end loop;
  return candidate;
end;
$$;

do $$
declare account record; workspace_uuid uuid;
begin
  for account in select id,display_name from public.profiles loop
    insert into public.workspaces(name,owner_id,invite_code)
    values(left(trim(account.display_name)||'''s Farm',80),account.id,public.new_workspace_invite_code())
    returning id into workspace_uuid;
    insert into public.workspace_members(workspace_id,user_id,role)
    values(workspace_uuid,account.id,'OWNER');
    update public.profiles set active_workspace_id=workspace_uuid where id=account.id;
  end loop;
end $$;

do $$
declare table_name text;
begin
  foreach table_name in array array[
    'inventory','inventory_transactions','prices','sales','sale_items','planting',
    'grafting_events','planting_updates','record_archive_events'
  ] loop
    execute format('alter table public.%I add column workspace_id uuid references public.workspaces(id) on delete cascade',table_name);
    execute format('update public.%I as target set workspace_id=w.id from public.workspaces w where w.owner_id=target.user_id',table_name);
    execute format('alter table public.%I alter column workspace_id set not null',table_name);
    execute format('create index %I on public.%I(workspace_id)',table_name||'_workspace',table_name);
  end loop;
end $$;

alter table public.inventory drop constraint if exists inventory_user_id_batch_number_size_grade_key;
alter table public.inventory add constraint inventory_workspace_batch_size_grade_key
  unique(workspace_id,batch_number,size,grade);
drop index if exists public.prices_one_active_category;
create unique index prices_one_active_workspace_category
  on public.prices(workspace_id,grade,coalesce(size,'')) where active;
alter table public.planting drop constraint if exists planting_user_id_record_number_key;
alter table public.planting add constraint planting_workspace_record_number_key unique(workspace_id,record_number);

create or replace function public.require_workspace_id()
returns uuid language plpgsql stable security definer set search_path=public,pg_temp as $$
declare workspace_uuid uuid;
begin
  select p.active_workspace_id into workspace_uuid
  from public.profiles p join public.workspace_members m
    on m.workspace_id=p.active_workspace_id and m.user_id=p.id
  where p.id=auth.uid();
  if workspace_uuid is null then raise exception 'Select a workspace first' using errcode='42501'; end if;
  return workspace_uuid;
end;
$$;

create or replace function public.require_owner_workspace_id()
returns uuid language plpgsql stable security definer set search_path=public,pg_temp as $$
declare workspace_uuid uuid:=public.require_workspace_id();
begin
  if not exists(select 1 from public.workspace_members where workspace_id=workspace_uuid and user_id=auth.uid() and role='OWNER') then
    raise exception 'Workspace owner permission is required' using errcode='42501';
  end if;
  return workspace_uuid;
end;
$$;

create or replace function public.is_workspace_member(p_workspace_id uuid)
returns boolean language sql stable security definer set search_path=public,pg_temp as $$
  select auth.uid() is not null and exists(
    select 1 from public.workspace_members where workspace_id=p_workspace_id and user_id=auth.uid());
$$;

create or replace function public.assign_active_workspace()
returns trigger language plpgsql security definer set search_path=public,pg_temp as $$
begin
  if auth.uid() is null then return new; end if;
  if new.workspace_id is null then new.workspace_id:=public.require_workspace_id(); end if;
  if new.workspace_id<>public.require_workspace_id() then raise exception 'Record workspace does not match the active workspace'; end if;
  if new.user_id is distinct from auth.uid() then new.user_id:=auth.uid(); end if;
  return new;
end;
$$;

do $$
declare table_name text;
begin
  foreach table_name in array array[
    'inventory','inventory_transactions','prices','sales','sale_items','planting',
    'grafting_events','planting_updates','record_archive_events'
  ] loop
    execute format('create trigger assign_%I_workspace before insert on public.%I for each row execute function public.assign_active_workspace()',table_name,table_name);
  end loop;
end $$;

create or replace function public.guard_owner_price_change()
returns trigger language plpgsql security definer set search_path=public,pg_temp as $$
begin
  perform public.require_owner_workspace_id();
  if tg_op='DELETE' then return old; end if;
  return new;
end;
$$;
create trigger owner_price_change before insert or update or delete on public.prices
for each row execute function public.guard_owner_price_change();

-- Re-scope existing RPC implementations from personal user records to the active workspace.
do $$
declare signature regprocedure; definition text; owner_only boolean;
begin
  foreach signature in array array[
    'public.inventory_transaction_json(uuid)'::regprocedure,
    'public.get_inventory()'::regprocedure,'public.get_inventory_batches()'::regprocedure,
    'public.get_inventory_details(bigint)'::regprocedure,'public.get_inventory_batch_details(text)'::regprocedure,
    'public.get_prices()'::regprocedure,'public.get_sales()'::regprocedure,'public.get_planting()'::regprocedure,
    'public.get_planting_details(bigint)'::regprocedure,'public.get_archived_records()'::regprocedure,
    'public.get_dashboard()'::regprocedure,'public.get_sales_analytics(text,date)'::regprocedure,
    'public.register_harvest(uuid,jsonb)'::regprocedure,'public.adjust_inventory(uuid,jsonb)'::regprocedure,
    'public.regrade_inventory(uuid,jsonb)'::regprocedure,'public.configure_price(uuid,jsonb)'::regprocedure,
    'public.create_planting(uuid,jsonb)'::regprocedure,'public.update_planting_stage(uuid,jsonb)'::regprocedure,
    'public.record_grafting(uuid,jsonb)'::regprocedure,'public.add_planting_update(uuid,jsonb)'::regprocedure,
    'public.archive_record(uuid,jsonb)'::regprocedure,'public.restore_record(uuid,jsonb)'::regprocedure,
    'public.complete_sale(uuid,jsonb)'::regprocedure
  ] loop
    select pg_get_functiondef(signature::oid) into definition;
    definition:=regexp_replace(definition,'([a-zA-Z_][a-zA-Z0-9_]*\.)?user_id\s*=\s*public\.require_user_id\(\)',
      '\1workspace_id=public.require_workspace_id()','gi');
    definition:=regexp_replace(definition,'([a-zA-Z_][a-zA-Z0-9_]*\.)?user_id\s*=\s*uid',
      '\1workspace_id=public.require_workspace_id()','gi');
    owner_only:=signature in (
      'public.configure_price(uuid,jsonb)'::regprocedure,'public.archive_record(uuid,jsonb)'::regprocedure,
      'public.restore_record(uuid,jsonb)'::regprocedure,'public.get_archived_records()'::regprocedure,
      'public.get_sales_analytics(text,date)'::regprocedure);
    if owner_only then
      definition:=replace(definition,'public.require_workspace_id()','public.require_owner_workspace_id()');
    end if;
    execute definition;
  end loop;
end $$;

create or replace function public.get_workspaces()
returns jsonb language sql stable security definer set search_path=public,pg_temp as $$
select coalesce(jsonb_agg(jsonb_build_object(
  'id',w.id,'name',w.name,'role',m.role,'active',p.active_workspace_id=w.id,
  'memberCount',(select count(*) from public.workspace_members x where x.workspace_id=w.id),
  'inviteCode',case when m.role='OWNER' then w.invite_code else null end)
  order by (p.active_workspace_id=w.id) desc,w.name),'[]'::jsonb)
from public.workspace_members m join public.workspaces w on w.id=m.workspace_id
join public.profiles p on p.id=m.user_id where m.user_id=public.require_user_id();
$$;

create or replace function public.get_workspace_members()
returns jsonb language sql stable security definer set search_path=public,pg_temp as $$
select coalesce(jsonb_agg(jsonb_build_object(
  'userId',p.id,'displayName',p.display_name,'email',p.email,'role',m.role)
  order by case m.role when 'OWNER' then 0 else 1 end,p.display_name),'[]'::jsonb)
from public.workspace_members m join public.profiles p on p.id=m.user_id
where m.workspace_id=public.require_workspace_id();
$$;

create or replace function public.select_workspace(p_workspace_id uuid)
returns jsonb language plpgsql security definer set search_path=public,pg_temp as $$
declare membership public.workspace_members%rowtype; workspace_name text;
begin
  select * into membership from public.workspace_members where workspace_id=p_workspace_id and user_id=public.require_user_id();
  if not found then raise exception 'You are not a member of that workspace'; end if;
  update public.profiles set active_workspace_id=p_workspace_id where id=public.require_user_id();
  select name into workspace_name from public.workspaces where id=p_workspace_id;
  return jsonb_build_object('id',p_workspace_id,'name',workspace_name,'role',membership.role);
end;
$$;

create or replace function public.create_workspace(p_name text)
returns jsonb language plpgsql security definer set search_path=public,pg_temp as $$
declare uid uuid:=public.require_user_id(); workspace_uuid uuid; code text:=public.new_workspace_invite_code();
declare sizes text[]:=array['EXTRA_SMALL','SMALL','MEDIUM','LARGE','JUMBO']; grade_a numeric[]:=array[70,80,100,120,140];
declare grade_b numeric[]:=array[60,70,85,100,115]; i integer;
begin
  if length(trim(p_name))<2 then raise exception 'Workspace name is required'; end if;
  insert into public.workspaces(name,owner_id,invite_code) values(trim(p_name),uid,code) returning id into workspace_uuid;
  insert into public.workspace_members values(workspace_uuid,uid,'OWNER',now());
  update public.profiles set active_workspace_id=workspace_uuid where id=uid;
  for i in 1..5 loop
    insert into public.prices(user_id,workspace_id,grade,size,price_per_kilogram)
    values(uid,workspace_uuid,'A',sizes[i],grade_a[i]),(uid,workspace_uuid,'B',sizes[i],grade_b[i]);
  end loop;
  insert into public.prices(user_id,workspace_id,grade,size,price_per_kilogram) values(uid,workspace_uuid,'C',null,50);
  return jsonb_build_object('id',workspace_uuid,'name',trim(p_name),'role','OWNER','inviteCode',code);
end;
$$;

create or replace function public.join_workspace(p_invite_code text)
returns jsonb language plpgsql security definer set search_path=public,pg_temp as $$
declare uid uuid:=public.require_user_id(); target public.workspaces%rowtype;
begin
  select * into target from public.workspaces where invite_code=upper(trim(p_invite_code));
  if not found then raise exception 'Invite code is invalid'; end if;
  insert into public.workspace_members(workspace_id,user_id,role) values(target.id,uid,'FARMER')
    on conflict(workspace_id,user_id) do nothing;
  update public.profiles set active_workspace_id=target.id where id=uid;
  return jsonb_build_object('id',target.id,'name',target.name,'role',
    (select role from public.workspace_members where workspace_id=target.id and user_id=uid));
end;
$$;

create or replace function public.regenerate_workspace_invite()
returns jsonb language plpgsql security definer set search_path=public,pg_temp as $$
declare workspace_uuid uuid:=public.require_owner_workspace_id(); code text:=public.new_workspace_invite_code();
begin update public.workspaces set invite_code=code where id=workspace_uuid; return jsonb_build_object('inviteCode',code); end;
$$;

create or replace function public.remove_workspace_member(p_user_id uuid)
returns jsonb language plpgsql security definer set search_path=public,pg_temp as $$
declare workspace_uuid uuid:=public.require_owner_workspace_id();
begin
  if p_user_id=auth.uid() then raise exception 'The owner cannot remove themselves'; end if;
  delete from public.workspace_members where workspace_id=workspace_uuid and user_id=p_user_id and role='FARMER';
  if not found then raise exception 'Farmer membership was not found'; end if;
  update public.profiles p set active_workspace_id=(select m.workspace_id from public.workspace_members m where m.user_id=p.id order by m.joined_at limit 1)
    where p.id=p_user_id and p.active_workspace_id=workspace_uuid;
  return jsonb_build_object('ok',true);
end;
$$;

create or replace function public.leave_workspace()
returns jsonb language plpgsql security definer set search_path=public,pg_temp as $$
declare uid uuid:=public.require_user_id(); workspace_uuid uuid:=public.require_workspace_id();
begin
  delete from public.workspace_members where workspace_id=workspace_uuid and user_id=uid and role='FARMER';
  if not found then raise exception 'Workspace owners cannot leave their workspace'; end if;
  update public.profiles set active_workspace_id=(select workspace_id from public.workspace_members where user_id=uid order by joined_at limit 1) where id=uid;
  return jsonb_build_object('ok',true);
end;
$$;

-- New accounts start with an owner workspace and workspace-scoped default prices.
create or replace function public.handle_new_user()
returns trigger language plpgsql security definer set search_path=public,pg_temp as $$
declare sizes text[]:=array['EXTRA_SMALL','SMALL','MEDIUM','LARGE','JUMBO']; grade_a numeric[]:=array[70,80,100,120,140];
declare grade_b numeric[]:=array[60,70,85,100,115]; i integer; profile_name text; workspace_uuid uuid;
begin
  profile_name:=coalesce(nullif(trim(new.raw_user_meta_data->>'display_name'),''),split_part(coalesce(new.email,''),'@',1));
  if length(profile_name)<2 then profile_name:='Farm User'; end if;
  insert into public.profiles(id,email,display_name) values(new.id,coalesce(new.email,''),profile_name);
  insert into public.workspaces(name,owner_id,invite_code) values(left(profile_name||'''s Farm',80),new.id,public.new_workspace_invite_code()) returning id into workspace_uuid;
  insert into public.workspace_members values(workspace_uuid,new.id,'OWNER',now());
  update public.profiles set active_workspace_id=workspace_uuid where id=new.id;
  for i in 1..5 loop
    insert into public.prices(user_id,workspace_id,grade,size,price_per_kilogram)
    values(new.id,workspace_uuid,'A',sizes[i],grade_a[i]),(new.id,workspace_uuid,'B',sizes[i],grade_b[i]);
  end loop;
  insert into public.prices(user_id,workspace_id,grade,size,price_per_kilogram) values(new.id,workspace_uuid,'C',null,50);
  return new;
end;
$$;

alter table public.workspaces enable row level security;
alter table public.workspace_members enable row level security;
create policy workspaces_member_select on public.workspaces for select to authenticated using(public.is_workspace_member(id));
create policy workspace_members_member_select on public.workspace_members for select to authenticated using(public.is_workspace_member(workspace_id));

do $$
declare table_name text;
begin
  foreach table_name in array array[
    'inventory','inventory_transactions','prices','sales','sale_items','planting',
    'grafting_events','planting_updates','record_archive_events'
  ] loop
    execute format('drop policy if exists %I on public.%I',table_name||'_select_own',table_name);
    execute format('create policy %I on public.%I for select to authenticated using(public.is_workspace_member(workspace_id))',table_name||'_select_workspace',table_name);
  end loop;
end $$;

revoke all on public.workspaces,public.workspace_members from anon,authenticated;
grant select on public.workspaces,public.workspace_members to authenticated;
revoke all on function public.new_workspace_invite_code(),public.require_workspace_id(),public.require_owner_workspace_id(),
  public.is_workspace_member(uuid),public.assign_active_workspace(),public.guard_owner_price_change() from public,anon,authenticated;
revoke all on function public.get_workspaces(),public.get_workspace_members(),public.select_workspace(uuid),public.create_workspace(text),
  public.join_workspace(text),public.regenerate_workspace_invite(),public.remove_workspace_member(uuid),public.leave_workspace() from public,anon;
grant execute on function public.get_workspaces(),public.get_workspace_members(),public.select_workspace(uuid),public.create_workspace(text),
  public.join_workspace(text),public.regenerate_workspace_invite(),public.remove_workspace_member(uuid),public.leave_workspace() to authenticated;
