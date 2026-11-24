create or replace function public.get_sales_analytics_bounded(
  p_period text,
  p_selected_date date,
  p_end_exclusive date
)
returns jsonb
language plpgsql
stable
security definer
set search_path=public,pg_temp
as $$
declare
  workspace_uuid uuid:=public.require_owner_workspace_id();
  normalized_period text:=case
    when lower(trim(p_period))='annual' then 'yearly'
    else lower(trim(p_period))
  end;
  range_start timestamp;
  range_end timestamp;
  previous_start timestamp;
  bucket_step interval;
  bucket_unit text;
  label_format text;
  previous_revenue numeric;
  revenue numeric;
  sale_count integer;
  pieces integer;
  weight numeric;
  comparison numeric;
  trend jsonb;
  summary jsonb;
begin
  if p_selected_date is null then
    raise exception 'Select a reporting date';
  end if;
  if normalized_period not in ('daily','weekly','monthly','yearly') then
    raise exception 'Unsupported reporting period';
  end if;

  if normalized_period='daily' then
    range_start:=p_selected_date::timestamp;
    range_end:=range_start+interval '1 day';
    previous_start:=range_start-interval '1 day';
    bucket_step:=interval '1 hour';
    bucket_unit:='hour';
    label_format:='HH12 AM';
  elsif normalized_period='weekly' then
    range_start:=date_trunc('week',p_selected_date)::timestamp;
    range_end:=range_start+interval '7 days';
    previous_start:=range_start-interval '7 days';
    bucket_step:=interval '1 day';
    bucket_unit:='day';
    label_format:='Dy DD';
  elsif normalized_period='monthly' then
    range_start:=date_trunc('month',p_selected_date)::timestamp;
    range_end:=range_start+interval '1 month';
    previous_start:=range_start-interval '1 month';
    bucket_step:=interval '1 day';
    bucket_unit:='day';
    label_format:='Mon DD';
  else
    range_start:=date_trunc('year',p_selected_date)::timestamp;
    range_end:=range_start+interval '1 year';
    previous_start:=range_start-interval '1 year';
    bucket_step:=interval '1 month';
    bucket_unit:='month';
    label_format:='Mon';
  end if;

  -- A yearly cutoff is inclusive. Keep the partial final month as a chart bucket.
  if p_end_exclusive is not null then
    if normalized_period <> 'yearly' or p_end_exclusive <= range_start::date
      or p_end_exclusive > range_end::date then
      raise exception 'Invalid yearly cutoff';
    end if;
    range_end:=p_end_exclusive::timestamp;
  end if;

  select coalesce(sum(s.total_amount),0),count(*)
  into revenue,sale_count
  from public.sales s
  where s.workspace_id=workspace_uuid
    and s.archived_at is null
    and s.completed_at>=(range_start at time zone 'Asia/Manila')
    and s.completed_at<(range_end at time zone 'Asia/Manila');

  select coalesce(sum(si.pieces),0),coalesce(sum(si.weight_kilograms),0)
  into pieces,weight
  from public.sale_items si
  join public.sales s on s.id=si.sale_id
  where s.workspace_id=workspace_uuid
    and s.archived_at is null
    and s.completed_at>=(range_start at time zone 'Asia/Manila')
    and s.completed_at<(range_end at time zone 'Asia/Manila');

  select coalesce(sum(s.total_amount),0)
  into previous_revenue
  from public.sales s
  where s.workspace_id=workspace_uuid
    and s.archived_at is null
    and s.completed_at>=(previous_start at time zone 'Asia/Manila')
    and s.completed_at<(range_start at time zone 'Asia/Manila');

  comparison:=case when previous_revenue=0 then null
    else round(((revenue-previous_revenue)/previous_revenue)*100,2) end;

  with bucket_series as (
    select generate_series(range_start,date_trunc(bucket_unit,range_end-interval '1 microsecond'),bucket_step) bucket
  ), bucket_totals as (
    select date_trunc(bucket_unit,s.completed_at at time zone 'Asia/Manila') bucket,
      sum(si.subtotal) bucket_revenue,
      sum(si.pieces) bucket_pieces
    from public.sales s
    join public.sale_items si on si.sale_id=s.id
    where s.workspace_id=workspace_uuid
      and s.archived_at is null
      and s.completed_at>=(range_start at time zone 'Asia/Manila')
      and s.completed_at<(range_end at time zone 'Asia/Manila')
    group by 1
  )
  select coalesce(jsonb_agg(jsonb_build_object(
    'label',to_char(series.bucket,label_format),
    'revenue',coalesce(totals.bucket_revenue,0),
    'pieces',coalesce(totals.bucket_pieces,0)) order by series.bucket),'[]'::jsonb)
  into trend
  from bucket_series series
  left join bucket_totals totals on totals.bucket=series.bucket;

  select coalesce(jsonb_agg(jsonb_build_object(
    'size',category.size,
    'grade',category.grade,
    'pieces',category.category_pieces,
    'weightKilograms',category.category_weight,
    'revenue',category.category_revenue)
    order by category.grade,category.size),'[]'::jsonb)
  into summary
  from (
    select si.size,si.grade,sum(si.pieces) category_pieces,
      sum(si.weight_kilograms) category_weight,
      sum(si.subtotal) category_revenue
    from public.sale_items si
    join public.sales s on s.id=si.sale_id
    where s.workspace_id=workspace_uuid
      and s.archived_at is null
      and s.completed_at>=(range_start at time zone 'Asia/Manila')
      and s.completed_at<(range_end at time zone 'Asia/Manila')
    group by si.size,si.grade
  ) category;

  return jsonb_build_object(
    'period',normalized_period,
    'selectedDate',p_selected_date::text,
    'previousRevenue',previous_revenue,
    'comparisonPercent',comparison,
    'totals',jsonb_build_object(
      'revenue',revenue,
      'completedSales',sale_count,
      'pieces',pieces,
      'weightKilograms',weight),
    'trend',trend,
    'summary',summary);
end;
$$;

-- Keep the existing RPC signature for older clients and full-period reports.
create or replace function public.get_sales_analytics(p_period text,p_selected_date date)
returns jsonb language sql stable security definer set search_path=public,pg_temp as $$
  select public.get_sales_analytics_bounded(p_period,p_selected_date,null);
$$;

create or replace function public.get_yearly_analytics_through_date(
  p_selected_date date,p_comparison_date date default null)
returns jsonb language plpgsql stable security definer set search_path=public,pg_temp as $$
declare workspace_uuid uuid:=public.require_owner_workspace_id();
declare current_start timestamp;
declare current_end timestamp;
declare comparison_start timestamp;
declare comparison_end timestamp;

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
  current_start:=date_trunc('year',p_selected_date::timestamp);
  current_end:=p_selected_date+1;
  comparison_start:=coalesce(date_trunc('year',p_comparison_date::timestamp),
    current_start-interval '1 year');
  -- Shift by calendar years and clamp February 29 in a non-leap comparison year.
  comparison_end:=(p_selected_date+make_interval(years =>
    extract(year from comparison_start)::integer-extract(year from current_start)::integer))::date+1;

  current_report:=public.get_sales_analytics_bounded('yearly',p_selected_date,current_end::date);
  comparison_report:=public.get_sales_analytics_bounded(
    'yearly',comparison_start::date,comparison_end::date);

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

  current_trend:=public.add_historical_revenue_to_trend(
    current_report->'trend',current_start,current_end,interval '1 month','month',workspace_uuid);
  comparison_trend:=public.add_historical_revenue_to_trend(
    comparison_report->'trend',comparison_start,comparison_end,interval '1 month','month',workspace_uuid);

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

revoke all on function public.get_sales_analytics_bounded(text,date,date) from public,anon,authenticated;
revoke all on function public.get_yearly_analytics_through_date(date,date) from public,anon;
grant execute on function public.get_yearly_analytics_through_date(date,date) to authenticated;
