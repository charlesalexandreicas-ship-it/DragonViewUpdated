-- Owner-only workspace soft deletion. Business records and memberships are retained.
alter table public.workspaces
  add column deleted_at timestamptz,
  add column deleted_by uuid references public.profiles(id) on delete set null;

create index workspaces_active_owner
  on public.workspaces(owner_id) where deleted_at is null;

create or replace function public.require_workspace_id()
returns uuid language plpgsql stable security definer set search_path=public,pg_temp as $$
declare workspace_uuid uuid;
begin
  select p.active_workspace_id into workspace_uuid
  from public.profiles p
  join public.workspace_members m
    on m.workspace_id=p.active_workspace_id and m.user_id=p.id
  join public.workspaces w
    on w.id=m.workspace_id and w.deleted_at is null
  where p.id=auth.uid();
  if workspace_uuid is null then
    raise exception 'Select a workspace first' using errcode='42501';
  end if;
  return workspace_uuid;
end;
$$;

create or replace function public.is_workspace_member(p_workspace_id uuid)
returns boolean language sql stable security definer set search_path=public,pg_temp as $$
  select auth.uid() is not null and exists(
    select 1
    from public.workspace_members m
    join public.workspaces w on w.id=m.workspace_id and w.deleted_at is null
    where m.workspace_id=p_workspace_id and m.user_id=auth.uid());
$$;

create or replace function public.get_workspaces()
returns jsonb language sql stable security definer set search_path=public,pg_temp as $$
select coalesce(jsonb_agg(jsonb_build_object(
  'id',w.id,'name',w.name,'role',m.role,'active',p.active_workspace_id=w.id,
  'memberCount',(select count(*) from public.workspace_members x where x.workspace_id=w.id),
  'inviteCode',case when m.role='OWNER' then w.invite_code else null end)
  order by (p.active_workspace_id=w.id) desc,w.name),'[]'::jsonb)
from public.workspace_members m
join public.workspaces w on w.id=m.workspace_id and w.deleted_at is null
join public.profiles p on p.id=m.user_id
where m.user_id=public.require_user_id();
$$;

create or replace function public.select_workspace(p_workspace_id uuid)
returns jsonb language plpgsql security definer set search_path=public,pg_temp as $$
declare membership public.workspace_members%rowtype; workspace_name text;
begin
  select m into membership
  from public.workspace_members m
  join public.workspaces w on w.id=m.workspace_id and w.deleted_at is null
  where m.workspace_id=p_workspace_id and m.user_id=public.require_user_id();
  if not found then raise exception 'You are not a member of that workspace'; end if;
  update public.profiles set active_workspace_id=p_workspace_id where id=public.require_user_id();
  select name into workspace_name from public.workspaces where id=p_workspace_id;
  return jsonb_build_object('id',p_workspace_id,'name',workspace_name,'role',membership.role);
end;
$$;

create or replace function public.join_workspace(p_invite_code text)
returns jsonb language plpgsql security definer set search_path=public,pg_temp as $$
declare uid uuid:=public.require_user_id(); target public.workspaces%rowtype;
begin
  select * into target from public.workspaces
  where invite_code=upper(trim(p_invite_code)) and deleted_at is null;
  if not found then raise exception 'Invite code is invalid'; end if;
  insert into public.workspace_members(workspace_id,user_id,role) values(target.id,uid,'FARMER')
    on conflict(workspace_id,user_id) do nothing;
  update public.profiles set active_workspace_id=target.id where id=uid;
  return jsonb_build_object('id',target.id,'name',target.name,'role',
    (select role from public.workspace_members where workspace_id=target.id and user_id=uid));
end;
$$;

create or replace function public.delete_workspace()
returns jsonb language plpgsql security definer set search_path=public,pg_temp as $$
declare
  uid uuid:=public.require_user_id();
  workspace_uuid uuid:=public.require_owner_workspace_id();
  next_workspace jsonb;
begin
  update public.workspaces
  set deleted_at=now(),deleted_by=uid,updated_at=now(),version=version+1
  where id=workspace_uuid and deleted_at is null;
  if not found then raise exception 'Workspace is already deleted'; end if;

  update public.profiles p
  set active_workspace_id=(
    select m.workspace_id
    from public.workspace_members m
    join public.workspaces w on w.id=m.workspace_id and w.deleted_at is null
    where m.user_id=p.id
    order by m.joined_at,m.workspace_id
    limit 1)
  where p.active_workspace_id=workspace_uuid;

  select jsonb_build_object('id',w.id,'name',w.name,'role',m.role)
  into next_workspace
  from public.profiles p
  join public.workspace_members m
    on m.workspace_id=p.active_workspace_id and m.user_id=p.id
  join public.workspaces w on w.id=m.workspace_id and w.deleted_at is null
  where p.id=uid;

  return jsonb_build_object('ok',true,'nextWorkspace',next_workspace);
end;
$$;

revoke all on function public.delete_workspace() from public,anon;
grant execute on function public.delete_workspace() to authenticated;

