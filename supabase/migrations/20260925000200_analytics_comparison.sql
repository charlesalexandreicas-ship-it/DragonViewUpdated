-- New RPC keeps get_sales_analytics(text,date) intact for older APKs.
-- Historical date-only revenue belongs in totals and day/month buckets, not an invented hour.
create function public.add_historical_revenue_to_trend(
  p_trend jsonb,p_start timestamp,p_end timestamp,p_step interval,
  p_unit text,p_workspace_id uuid)
returns jsonb language sql stable security invoker set search_path=public,pg_temp as $$
  select coalesce(jsonb_agg(jsonb_set(entry.value,'{revenue}',
    to_jsonb((entry.value->>'revenue')::numeric+coalesce(paper.revenue,0)))
    order by entry.ordinality),'[]'::jsonb)
  from jsonb_array_elements(coalesce(p_trend,'[]'::jsonb))
    with ordinality as entry(value,ordinality)
  left join (
    select date_trunc(p_unit,h.record_date::timestamp) bucket,
      sum(i.subtotal) revenue
    from public.historical_records h
    join public.historical_record_items i on i.record_id=h.id
    where h.workspace_id=p_workspace_id and i.actual_sales
      and h.record_date>=p_start::date and h.record_date<p_end::date
    group by 1
  ) paper on paper.bucket=date_trunc(p_unit,
    p_start+p_step*(entry.ordinality-1)::double precision);
$$;

revoke all on function public.add_historical_revenue_to_trend(
  jsonb,timestamp,timestamp,interval,text,uuid) from public,anon,authenticated;

create function public.get_sales_analytics_v2(
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

  select coalesce(sum(i.subtotal),0) into current_paper
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
    'weightKilograms',0,'revenue',paper.revenue) order by paper.size),'[]'::jsonb)
  into paper_summary
  from (
    select i.size,sum(i.pieces) pieces,sum(i.subtotal) revenue
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
    'totals',jsonb_set(current_report->'totals','{revenue}',to_jsonb(current_revenue)),
    'trend',current_trend,
    'comparisonTrend',comparison_trend,
    'summary',coalesce(current_report->'summary','[]'::jsonb)||paper_summary);
end;
$$;

revoke all on function public.get_sales_analytics_v2(text,date,date) from public,anon;
grant execute on function public.get_sales_analytics_v2(text,date,date) to authenticated;
