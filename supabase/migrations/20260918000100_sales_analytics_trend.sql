-- Complete, workspace-scoped trend buckets for every reporting period.
-- "annual" remains accepted for compatibility with older APKs.
create or replace function public.get_sales_analytics(
  p_period text,
  p_selected_date date
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
    select generate_series(range_start,range_end-bucket_step,bucket_step) bucket
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

revoke all on function public.get_sales_analytics(text,date) from public,anon;
grant execute on function public.get_sales_analytics(text,date) to authenticated;
