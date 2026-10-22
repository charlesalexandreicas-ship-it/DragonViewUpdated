-- Owner-reviewed, date-only paper records. No stock or FIFO mutation occurs here.
create table public.historical_records (
  id uuid primary key default extensions.gen_random_uuid(),
  workspace_id uuid not null references public.workspaces(id) on delete cascade,
  created_by uuid references public.profiles(id) on delete set null,
  record_date date not null,
  source text not null check (source in ('MANUAL','PHOTO')),
  paper_grand_total numeric(14,2) check (paper_grand_total >= 0),
  total_amount numeric(14,2) not null check (total_amount >= 0),
  created_at timestamptz not null default now()
);

create table public.historical_record_items (
  id uuid primary key default extensions.gen_random_uuid(),
  record_id uuid not null references public.historical_records(id) on delete cascade,
  position integer not null check (position >= 0),
  size text not null check (size in ('EXTRA_SMALL','SMALL','MEDIUM','LARGE','JUMBO')),
  pieces integer not null check (pieces > 0),
  price_per_kilogram numeric(14,2) not null check (price_per_kilogram >= 0),
  subtotal numeric(14,2) not null check (subtotal >= 0),
  actual_sales boolean not null,
  unique (record_id,position)
);

create index historical_records_workspace_date
  on public.historical_records(workspace_id,record_date desc);
create index historical_record_items_parent
  on public.historical_record_items(record_id,position);

alter table public.historical_records enable row level security;
alter table public.historical_record_items enable row level security;

create policy historical_records_owner_select on public.historical_records
  for select to authenticated using (
    exists(select 1 from public.workspace_members m
      where m.workspace_id=historical_records.workspace_id
        and m.user_id=auth.uid() and m.role='OWNER')
  );
create policy historical_record_items_owner_select on public.historical_record_items
  for select to authenticated using (
    exists(select 1 from public.historical_records h
      join public.workspace_members m on m.workspace_id=h.workspace_id
      where h.id=historical_record_items.record_id
        and m.user_id=auth.uid() and m.role='OWNER')
  );

revoke all on public.historical_records,public.historical_record_items from anon,authenticated;
grant select on public.historical_records,public.historical_record_items to authenticated;

create function public.get_historical_records()
returns jsonb language plpgsql stable security definer set search_path=public,pg_temp as $$
declare workspace_uuid uuid:=public.require_owner_workspace_id();
declare result jsonb;
begin
  select coalesce(jsonb_agg(jsonb_build_object(
    'id',h.id,'date',h.record_date::text,'source',h.source,
    'paperGrandTotal',h.paper_grand_total,
    'totalAmount',h.total_amount,'salesAmount',coalesce((
      select sum(i.subtotal) from public.historical_record_items i
      where i.record_id=h.id and i.actual_sales),0),
    'createdAt',h.created_at,
    'items',coalesce((select jsonb_agg(jsonb_build_object(
      'size',i.size,'pieces',i.pieces,'pricePerKilogram',i.price_per_kilogram,
      'subtotal',i.subtotal,'actualSales',i.actual_sales) order by i.position)
      from public.historical_record_items i where i.record_id=h.id),'[]'::jsonb))
    order by h.record_date desc,h.created_at desc),'[]'::jsonb)
  into result
  from public.historical_records h where h.workspace_id=workspace_uuid;
  return result;
end;
$$;

create function public.save_historical_record(p_operation_id uuid,p_payload jsonb)
returns jsonb language plpgsql security definer set search_path=public,pg_temp as $$
declare workspace_uuid uuid:=public.require_owner_workspace_id();
declare prior jsonb;
declare entry jsonb;
declare record_uuid uuid;
declare record_date date;
declare source_name text;
declare paper_total numeric(14,2);
declare computed_total numeric(14,2):=0;
declare row_pieces integer;
declare row_price numeric(14,2);
declare row_subtotal numeric(14,2);
declare row_size text;
declare row_actual_sales boolean;
declare row_position integer:=0;
declare result jsonb;
begin
  prior:=public.previous_request(p_operation_id,'save_historical_record');
  if prior is not null then return prior; end if;
  if p_payload is null or jsonb_typeof(p_payload) <> 'object' then
    raise exception 'Historical record details are required';
  end if;
  if coalesce(p_payload->>'date','') !~ '^[0-9]{4}-[0-9]{2}-[0-9]{2}$' then
    raise exception 'Enter the date including the year';
  end if;
  record_date:=(p_payload->>'date')::date;
  if record_date>(now() at time zone 'Asia/Manila')::date then
    raise exception 'Historical record date cannot be in the future';
  end if;
  source_name:=upper(coalesce(p_payload->>'source','MANUAL'));
  if source_name not in ('MANUAL','PHOTO') then raise exception 'Invalid record source'; end if;
  if jsonb_typeof(p_payload->'items') is distinct from 'array' then
    raise exception 'Add between 1 and 100 size rows';
  end if;
  if jsonb_array_length(p_payload->'items') not between 1 and 100 then
    raise exception 'Add between 1 and 100 size rows';
  end if;
  paper_total:=nullif(p_payload->>'paperGrandTotal','')::numeric(14,2);
  if paper_total < 0 then raise exception 'Grand total cannot be negative'; end if;

  insert into public.historical_records(
    workspace_id,created_by,record_date,source,paper_grand_total,total_amount)
  values(workspace_uuid,auth.uid(),record_date,source_name,paper_total,0)
  returning id into record_uuid;

  for entry in select value from jsonb_array_elements(p_payload->'items') loop
    row_size:=upper(replace(replace(trim(coalesce(entry->>'size','')),' ','_'),'-','_'));
    row_pieces:=(entry->>'pieces')::integer;
    row_price:=(entry->>'pricePerKilogram')::numeric(14,2);
    row_subtotal:=(entry->>'subtotal')::numeric(14,2);
    if jsonb_typeof(entry->'actualSales') is distinct from 'boolean' then
      raise exception 'Choose actual sales or estimated value for every row';
    end if;
    row_actual_sales:=(entry->>'actualSales')::boolean;
    if row_size not in ('EXTRA_SMALL','SMALL','MEDIUM','LARGE','JUMBO')
      or row_pieces is null or row_pieces <= 0
      or row_price is null or row_price < 0
      or row_subtotal is null or row_subtotal < 0 then
      raise exception 'Check each size, pieces, price per kilogram, and subtotal';
    end if;
    insert into public.historical_record_items(
      record_id,position,size,pieces,price_per_kilogram,subtotal,actual_sales)
    values(record_uuid,row_position,row_size,row_pieces,row_price,row_subtotal,
      row_actual_sales);
    computed_total:=computed_total+row_subtotal;
    row_position:=row_position+1;
  end loop;
  if paper_total is not null and abs(computed_total-paper_total)>0.01 then
    raise exception 'Grand total on paper does not match the subtotals';
  end if;
  update public.historical_records set total_amount=computed_total where id=record_uuid;
  result:=jsonb_build_object('ok',true,'id',record_uuid);
  return public.remember_request(p_operation_id,'save_historical_record',result);
end;
$$;

revoke all on function public.get_historical_records() from public,anon;
revoke all on function public.save_historical_record(uuid,jsonb) from public,anon;
grant execute on function public.get_historical_records() to authenticated;
grant execute on function public.save_historical_record(uuid,jsonb) to authenticated;
