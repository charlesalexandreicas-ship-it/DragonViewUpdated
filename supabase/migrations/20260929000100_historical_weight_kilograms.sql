-- Historical paper rows now record kilograms; legacy piece counts remain untouched.
-- Forward-only expansion: no old quantity is guessed or converted to weight.
alter table public.historical_record_items
  add column weight_kilograms numeric(14,3);
alter table public.historical_record_items
  alter column pieces drop not null;
alter table public.historical_record_items
  add constraint historical_record_items_weight_positive
    check (weight_kilograms is null or weight_kilograms > 0),
  add constraint historical_record_items_one_quantity
    check ((weight_kilograms is null) <> (pieces is null));

create or replace function public.get_historical_records()
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
      'size',i.size,'pieces',i.pieces,'weightKilograms',i.weight_kilograms,
      'pricePerKilogram',i.price_per_kilogram,
      'subtotal',i.subtotal,'actualSales',i.actual_sales) order by i.position)
      from public.historical_record_items i where i.record_id=h.id),'[]'::jsonb))
    order by h.record_date desc,h.created_at desc),'[]'::jsonb)
  into result
  from public.historical_records h where h.workspace_id=workspace_uuid;
  return result;
end;
$$;

-- A separate RPC leaves the old piece-based save contract available to older APKs.
create function public.save_historical_record_v2(p_operation_id uuid,p_payload jsonb)
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
    if row_size not in ('EXTRA_SMALL','SMALL','MEDIUM','LARGE','JUMBO')
      or row_weight is null or row_weight <= 0
      or row_price is null or row_price < 0
      or row_subtotal is null or row_subtotal < 0 then
      raise exception 'Check each size, kilograms, price per kilogram, and subtotal';
    end if;
    -- The owner's recorded amount remains authoritative only within one cent.
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

-- Preserve the original revenue and date logic while adding only known historical kg.
create or replace function public.get_sales_analytics_v2(
  p_period text,p_selected_date date,p_comparison_date date default null)
returns jsonb language plpgsql stable security definer set search_path=public,pg_temp as $$
declare workspace_uuid uuid:=public.require_owner_workspace_id();
declare normalized_period text:=case when lower(trim(p_period))='annual' then 'yearly'
  else lower(trim(p_period)) end;
declare current_start timestamp;
declare current_end timestamp;
declare comparison_start timestamp;
declare comparison_end timestamp;
declare previous_start timestamp;
declare bucket_step interval;
declare bucket_unit text;
declare current_report jsonb;
declare comparison_report jsonb;
declare current_paper numeric;
declare comparison_paper numeric;
declare current_paper_weight numeric;
declare current_revenue numeric;
declare comparison_revenue numeric;
declare percent_change numeric;
declare current_trend jsonb;
declare comparison_trend jsonb;
declare paper_summary jsonb;
begin
  if p_selected_date is null then raise exception 'Select a reporting date'; end if;
  if normalized_period not in ('daily','weekly','monthly','yearly') then
    raise exception 'Unsupported reporting period';
  end if;
  if normalized_period='daily' then
    current_start:=p_selected_date::timestamp;
    current_end:=current_start+interval '1 day';
    previous_start:=current_start-interval '1 day';
    bucket_step:=interval '1 hour'; bucket_unit:='hour';
  elsif normalized_period='weekly' then
    current_start:=date_trunc('week',p_selected_date::timestamp);
    current_end:=current_start+interval '7 days';
    previous_start:=current_start-interval '7 days';
    bucket_step:=interval '1 day'; bucket_unit:='day';
  elsif normalized_period='monthly' then
    current_start:=date_trunc('month',p_selected_date::timestamp);
    current_end:=current_start+interval '1 month';
    previous_start:=current_start-interval '1 month';
    bucket_step:=interval '1 day'; bucket_unit:='day';
  else
    current_start:=date_trunc('year',p_selected_date::timestamp);
    current_end:=current_start+interval '1 year';
    previous_start:=current_start-interval '1 year';
    bucket_step:=interval '1 month'; bucket_unit:='month';
  end if;

  if p_comparison_date is null then
    comparison_start:=previous_start;
  elsif normalized_period='daily' then
    comparison_start:=p_comparison_date::timestamp;
  elsif normalized_period='weekly' then
    comparison_start:=date_trunc('week',p_comparison_date::timestamp);
  elsif normalized_period='monthly' then
    comparison_start:=date_trunc('month',p_comparison_date::timestamp);
  else
    comparison_start:=date_trunc('year',p_comparison_date::timestamp);
  end if;
  if normalized_period='daily' then
    comparison_end:=comparison_start+interval '1 day';
  elsif normalized_period='weekly' then
    comparison_end:=comparison_start+interval '7 days';
  elsif normalized_period='monthly' then
    comparison_end:=comparison_start+interval '1 month';
  else
    comparison_end:=comparison_start+interval '1 year';
  end if;

  current_report:=public.get_sales_analytics(normalized_period,p_selected_date);
  comparison_report:=public.get_sales_analytics(normalized_period,comparison_start::date);

  select coalesce(sum(i.subtotal),0),coalesce(sum(i.weight_kilograms),0)
    into current_paper,current_paper_weight
  from public.historical_records h
  join public.historical_record_items i on i.record_id=h.id
  where h.workspace_id=workspace_uuid and i.actual_sales
    and h.record_date>=current_start::date and h.record_date<current_end::date;
  select coalesce(sum(i.subtotal),0) into comparison_paper
  from public.historical_records h
  join public.historical_record_items i on i.record_id=h.id
  where h.workspace_id=workspace_uuid and i.actual_sales
    and h.record_date>=comparison_start::date and h.record_date<comparison_end::date;

  current_revenue:=(current_report->'totals'->>'revenue')::numeric+current_paper;
  comparison_revenue:=(comparison_report->'totals'->>'revenue')::numeric+comparison_paper;
  percent_change:=case when comparison_revenue=0 then null
    else round(((current_revenue-comparison_revenue)/comparison_revenue)*100,2) end;

  if normalized_period='daily' then
    -- A handwritten date does not identify an hour; do not manufacture a spike.
    current_trend:=current_report->'trend';
    comparison_trend:=comparison_report->'trend';
  else
    current_trend:=public.add_historical_revenue_to_trend(
      current_report->'trend',current_start,current_end,bucket_step,bucket_unit,workspace_uuid);
    comparison_trend:=public.add_historical_revenue_to_trend(
      comparison_report->'trend',comparison_start,comparison_end,bucket_step,bucket_unit,workspace_uuid);
  end if;

  select coalesce(jsonb_agg(jsonb_build_object(
    'size',paper.size,'grade',null,'pieces',paper.pieces,
    'weightKilograms',paper.weight,'revenue',paper.revenue) order by paper.size),'[]'::jsonb)
  into paper_summary
  from (
    select i.size,sum(coalesce(i.pieces,0)) pieces,
      sum(coalesce(i.weight_kilograms,0)) weight,sum(i.subtotal) revenue
    from public.historical_records h
    join public.historical_record_items i on i.record_id=h.id
    where h.workspace_id=workspace_uuid and i.actual_sales
      and h.record_date>=current_start::date and h.record_date<current_end::date
    group by i.size
  ) paper;

  return current_report || jsonb_build_object(
    'comparisonDate',comparison_start::date::text,
    'currentStart',current_start::date::text,
    'comparisonStart',comparison_start::date::text,
    'previousRevenue',comparison_revenue,
    'comparisonPercent',percent_change,
    'historicalRevenue',current_paper,
    'comparisonHistoricalRevenue',comparison_paper,
    'totals',jsonb_set(jsonb_set(current_report->'totals','{revenue}',
      to_jsonb(current_revenue)),'{weightKilograms}',
      to_jsonb((current_report->'totals'->>'weightKilograms')::numeric+current_paper_weight)),
    'trend',current_trend,
    'comparisonTrend',comparison_trend,
    'summary',coalesce(current_report->'summary','[]'::jsonb)||paper_summary);
end;
$$;
