-- Allow the trusted auth.users trigger to seed a new owner's initial prices.
-- Ordinary price changes still require ownership of the active workspace.
create or replace function public.guard_owner_price_change()
returns trigger
language plpgsql
security definer
set search_path=public,pg_temp
as $$
declare
  record_workspace uuid:=case when tg_op='DELETE' then old.workspace_id else new.workspace_id end;
begin
  if auth.uid() is null
    and tg_op='INSERT'
    and pg_trigger_depth()>1
    and exists(
      select 1
      from public.workspaces w
      join public.workspace_members m
        on m.workspace_id=w.id
       and m.user_id=w.owner_id
       and m.role='OWNER'
      where w.id=new.workspace_id
        and w.owner_id=new.user_id
    ) then
    return new;
  end if;

  if record_workspace<>public.require_owner_workspace_id() then
    raise exception 'Price record does not belong to the active workspace' using errcode='42501';
  end if;

  if tg_op='DELETE' then return old; end if;
  return new;
end;
$$;

revoke all on function public.guard_owner_price_change() from public,anon,authenticated;
