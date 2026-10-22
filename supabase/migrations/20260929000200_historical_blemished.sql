-- Blemished is a historical-paper category only; live inventory sizes stay unchanged.
alter table public.historical_record_items
  drop constraint if exists historical_record_items_size_check;
alter table public.historical_record_items
  add constraint historical_record_items_size_check
    check (size in ('EXTRA_SMALL','SMALL','MEDIUM','LARGE','JUMBO','BLEMISHED'));

-- Keep the owner-only, idempotent kilogram save contract; extend only its category check.
create or replace function public.save_historical_record_v2(p_operation_id uuid,p_payload jsonb)
returns jsonb language plpgsql security definer set search_path=public,pg_temp as $$
declare workspace_uuid uuid:=public.require_owner_workspace_id();
declare prior jsonb;
declare entry jsonb;
declare record_uuid uuid;
declare record_date date;
declare source_name text;
declare paper_total numeric(14,2);
declare computed_total numeric(14,2):=0;
declare row_weight numeric(14,3);
declare row_price numeric(14,2);
declare row_subtotal numeric(14,2);
declare row_size text;
declare row_actual_sales boolean;
declare row_position integer:=0;
declare result jsonb;
begin
  prior:=public.previous_request(p_operation_id,'save_historical_record_v2');
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
    row_weight:=(entry->>'weightKilograms')::numeric(14,3);
    row_price:=(entry->>'pricePerKilogram')::numeric(14,2);
    row_subtotal:=(entry->>'subtotal')::numeric(14,2);
    if jsonb_typeof(entry->'actualSales') is distinct from 'boolean' then
      raise exception 'Choose actual sales or estimated value for every row';
    end if;
    row_actual_sales:=(entry->>'actualSales')::boolean;
    if row_size not in ('EXTRA_SMALL','SMALL','MEDIUM','LARGE','JUMBO','BLEMISHED')
      or row_weight is null or row_weight <= 0
      or row_price is null or row_price < 0
      or row_subtotal is null or row_subtotal < 0 then
      raise exception 'Check each size or condition, kilograms, price per kilogram, and subtotal';
    end if;
    if abs(round(row_weight*row_price,2)-row_subtotal)>0.01 then
      raise exception 'Each subtotal must match kilograms times price per kg';
    end if;
    insert into public.historical_record_items(
      record_id,position,size,weight_kilograms,price_per_kilogram,subtotal,actual_sales)
    values(record_uuid,row_position,row_size,row_weight,row_price,row_subtotal,
      row_actual_sales);
    computed_total:=computed_total+row_subtotal;
    row_position:=row_position+1;
  end loop;
  if paper_total is not null and abs(computed_total-paper_total)>0.01 then
    raise exception 'Grand total on paper does not match the subtotals';
  end if;
  update public.historical_records set total_amount=computed_total where id=record_uuid;
  result:=jsonb_build_object('ok',true,'id',record_uuid);
  return public.remember_request(p_operation_id,'save_historical_record_v2',result);
end;
$$;

revoke all on function public.save_historical_record_v2(uuid,jsonb) from public,anon;
grant execute on function public.save_historical_record_v2(uuid,jsonb) to authenticated;
