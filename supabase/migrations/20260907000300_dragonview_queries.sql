create or replace function public.inventory_transaction_json(p_inventory uuid)
returns jsonb language sql stable security definer set search_path=public,pg_temp as $$
select coalesce(jsonb_agg(jsonb_build_object(
    'id',display_id,'type',type,'remarks',remarks,
    'createdAt',to_char(created_at at time zone 'Asia/Manila','YYYY-MM-DD HH24:MI'),
    'createdBy',created_by,'pieces',pieces) order by created_at,display_id),'[]'::jsonb)
from public.inventory_transactions where user_id=public.require_user_id() and inventory_id=p_inventory;
$$;

create or replace function public.planting_group_json(p public.planting)
returns jsonb language plpgsql stable security definer set search_path=public,pg_temp as $$
declare days integer:=greatest(0,current_date-p.grafting_date); suggested text; fruit_days integer; window_text text;
begin
    if p.propagation_method='SEED' then
        suggested:=case when days<1 then 'SEED_SOWING' when days<15 then 'GERMINATION' when days<32 then 'SEEDLING_ESTABLISHMENT'
            when days<51 then 'EARLY_CACTUS_GROWTH' when days<76 then 'JUVENILE_STEM_GROWTH' when days<108 then 'ADVANCED_NURSERY_GROWTH' else 'TRANSFER_OR_GRAFTING' end;
    elsif p.propagation_method='GRAFTED' then
        suggested:=case when days<1 then 'GRAFT_PREPARATION' when days<15 then 'GRAFT_UNION' when days<29 then 'GRAFT_RECOVERY' when days<91 then 'GRAFTED_ESTABLISHMENT' else 'MATURE_GROWTH' end;
    else
        suggested:=case when days<1 then 'PLANTED' when days<22 then 'ESTABLISHMENT' when days<43 then 'ROOTING_AND_BUD' when days<71 then 'SHOOT_DEVELOPMENT'
            when days<99 then 'TRELLIS_TRAINING' when days<151 then 'TRELLIS_REACHED' when days<211 then 'CANOPY_DEVELOPMENT' else 'MATURE_GROWTH' end;
    end if;
    if p.flowering_date is not null then
        fruit_days:=greatest(0,current_date-p.flowering_date);
        window_text:=to_char(p.flowering_date+35,'YYYY-MM-DD')||' to '||to_char(p.flowering_date+45,'YYYY-MM-DD');
    end if;
    return jsonb_build_object('id',p.display_id,'recordNumber',p.record_number,'plantingDate',p.grafting_date::text,
        'variety',p.variety,'location',p.location,'numberOfPlants',p.number_of_plants,'cuttingType',p.cutting_type,
        'propagationMethod',p.propagation_method,'elapsedDays',days,'currentStage',p.current_stage,'suggestedStage',suggested,
        'fruitAgeDays',fruit_days,'estimatedHarvestWindow',window_text);
end;
$$;

create or replace function public.get_inventory()
returns jsonb language sql stable security definer set search_path=public,pg_temp as $$
with ranked as (
 select i.*,row_number() over(partition by size,grade order by harvest_date,display_id) rn
 from public.inventory i where user_id=public.require_user_id() and archived_at is null and available_pieces>0)
select coalesce(jsonb_agg(jsonb_build_object('id',display_id,'batchNumber',batch_number,'harvestDate',harvest_date::text,
 'size',size,'grade',grade,'availablePieces',available_pieces,'nextOut',rn=1) order by harvest_date,display_id),'[]'::jsonb) from ranked;
$$;

create or replace function public.get_inventory_batches()
returns jsonb language sql stable security definer set search_path=public,pg_temp as $$
with ranked as (
 select i.*,row_number() over(partition by size,grade order by harvest_date,display_id) rn
 from public.inventory i where user_id=public.require_user_id() and archived_at is null and available_pieces>0), grouped as (
 select batch_number,min(harvest_date) harvest_date,count(*) item_count,sum(available_pieces) pieces,count(*) filter(where rn=1) next_count
 from ranked group by batch_number)
select coalesce(jsonb_agg(jsonb_build_object('batchNumber',batch_number,'harvestDate',harvest_date::text,
 'itemCount',item_count,'availablePieces',pieces,'nextOutItems',next_count) order by harvest_date,batch_number),'[]'::jsonb) from grouped;
$$;

create or replace function public.get_inventory_details(p_inventory_id bigint)
returns jsonb language sql stable security definer set search_path=public,pg_temp as $$
select jsonb_build_object('id',display_id,'batchNumber',batch_number,'harvestDate',harvest_date::text,'size',size,'grade',grade,
 'availablePieces',available_pieces,'transactions',public.inventory_transaction_json(id))
from public.inventory where user_id=public.require_user_id() and display_id=p_inventory_id and archived_at is null;
$$;

create or replace function public.get_inventory_batch_details(p_batch_number text)
returns jsonb language sql stable security definer set search_path=public,pg_temp as $$
with active_ranked as (
 select id,row_number() over(partition by size,grade order by harvest_date,display_id) rn
 from public.inventory where user_id=public.require_user_id() and archived_at is null and available_pieces>0), rows as (
 select i.*,coalesce(r.rn=1,false) next_out from public.inventory i left join active_ranked r on r.id=i.id
 where i.user_id=public.require_user_id() and i.batch_number=p_batch_number and i.archived_at is null)
select case when count(*)=0 then null else jsonb_build_object('batchNumber',p_batch_number,'harvestDate',min(harvest_date)::text,
 'availablePieces',sum(available_pieces),'items',jsonb_agg(jsonb_build_object('id',display_id,'size',size,'grade',grade,
 'originalPieces',original_pieces,'availablePieces',available_pieces,'nextOut',next_out,
 'transactions',public.inventory_transaction_json(id)) order by size,grade,display_id)) end from rows;
$$;

create or replace function public.get_prices()
returns jsonb language sql stable security definer set search_path=public,pg_temp as $$
select coalesce(jsonb_agg(jsonb_build_object('id',display_id,'grade',grade,'size',size,'pricePerKilogram',price_per_kilogram)
 order by grade,size),'[]'::jsonb) from public.prices where user_id=public.require_user_id() and active;
$$;

create or replace function public.get_sales()
returns jsonb language sql stable security definer set search_path=public,pg_temp as $$
select coalesce(jsonb_agg(jsonb_build_object('id',s.display_id,'customerName',s.customer_name,'status',s.status,
 'paymentStatus',s.payment_status,'paymentMethod',s.payment_method,'totalAmount',s.total_amount,
 'totalPieces',coalesce((select sum(si.pieces) from public.sale_items si where si.sale_id=s.id),0),
 'transactionDate',to_char(s.completed_at at time zone 'Asia/Manila','YYYY-MM-DD HH24:MI')) order by s.completed_at desc,s.display_id desc),'[]'::jsonb)
from public.sales s where s.user_id=public.require_user_id() and s.archived_at is null;
$$;

create or replace function public.get_planting()
returns jsonb language sql stable security definer set search_path=public,pg_temp as $$
select coalesce(jsonb_agg(public.planting_group_json(p) order by grafting_date,display_id),'[]'::jsonb)
from public.planting p where user_id=public.require_user_id() and archived_at is null;
$$;

create or replace function public.get_planting_details(p_planting_id bigint)
returns jsonb language plpgsql stable security definer set search_path=public,pg_temp as $$
declare target public.planting%rowtype; updates jsonb;
begin
 select * into target from public.planting where user_id=public.require_user_id() and display_id=p_planting_id and archived_at is null;
 if not found then return null; end if;
 select coalesce(jsonb_agg(jsonb_build_object('id',display_id,'type',type,'stage',stage,'note',note,
  'measurementCentimeters',measurement_centimeters,'photoUri',photo_uri,'recordedDate',recorded_date::text)
  order by recorded_date desc,created_at desc,display_id desc),'[]'::jsonb) into updates
 from public.planting_updates where user_id=public.require_user_id() and planting_id=target.id;
 return jsonb_build_object('group',public.planting_group_json(target),'guidance',target.current_stage,'updates',updates);
end;
$$;

create or replace function public.get_archived_records()
returns jsonb language sql stable security definer set search_path=public,pg_temp as $$
with records as (
 select 'INVENTORY_BATCH' type,batch_number key,'Batch #'||batch_number title,sum(available_pieces)||' pieces unavailable while archived' details,
        max(archive_reason) reason,max(archived_at) archived_at
 from public.inventory where user_id=public.require_user_id() and archived_at is not null group by batch_number
 union all
 select 'PLANTING',display_id::text,'Farm Section '||record_number,location||' • '||variety,archive_reason,archived_at
 from public.planting where user_id=public.require_user_id() and archived_at is not null
 union all
 select 'SALE',display_id::text,'Sale #'||display_id,customer_name||' • accounting history retained',archive_reason,archived_at
 from public.sales where user_id=public.require_user_id() and archived_at is not null)
select coalesce(jsonb_agg(jsonb_build_object('type',type,'key',key,'title',title,'details',details,'reason',reason,
 'archivedAt',(extract(epoch from archived_at)*1000)::bigint) order by archived_at desc),'[]'::jsonb) from records;
$$;

create or replace function public.get_dashboard()
returns jsonb language plpgsql stable security definer set search_path=public,pg_temp as $$
declare uid uuid:=public.require_user_id(); today_start timestamptz:=(current_date::timestamp at time zone 'Asia/Manila');
declare month_start timestamptz:=(date_trunc('month',current_date)::timestamp at time zone 'Asia/Manila');
declare inventory_pieces integer; batches integer; sales_today integer; monthly numeric; plantings integer;
declare revenue_today numeric; pieces_today integer; weight_today numeric; yesterday numeric; change numeric;
begin
 select coalesce(sum(available_pieces),0),count(distinct batch_number) into inventory_pieces,batches from public.inventory where user_id=uid and archived_at is null and available_pieces>0;
 select count(*),coalesce(sum(total_amount),0) into sales_today,revenue_today from public.sales where user_id=uid and archived_at is null and completed_at>=today_start and completed_at<today_start+interval '1 day';
 select coalesce(sum(total_amount),0) into monthly from public.sales where user_id=uid and archived_at is null and completed_at>=month_start and completed_at<month_start+interval '1 month';
 select count(*) into plantings from public.planting where user_id=uid and archived_at is null;
 select coalesce(sum(si.pieces),0),coalesce(sum(si.weight_kilograms),0) into pieces_today,weight_today from public.sale_items si join public.sales s on s.id=si.sale_id where s.user_id=uid and s.archived_at is null and s.completed_at>=today_start and s.completed_at<today_start+interval '1 day';
 select coalesce(sum(total_amount),0) into yesterday from public.sales where user_id=uid and archived_at is null and completed_at>=today_start-interval '1 day' and completed_at<today_start;
 change:=case when yesterday=0 then null else round(((revenue_today-yesterday)/yesterday)*100,2) end;
 return jsonb_build_object('summary',jsonb_build_object('inventoryPieces',inventory_pieces,'activeBatches',batches,'salesToday',sales_today,
  'monthlyRevenue',monthly,'plantingGroups',plantings,'classificationsToday',0),'analyticsOverview',jsonb_build_object(
  'revenueToday',revenue_today,'piecesSoldToday',pieces_today,'weightSoldToday',weight_today,'revenueChangePercent',change));
end;
$$;

create or replace function public.get_sales_analytics(p_period text, p_selected_date date)
returns jsonb language plpgsql stable security definer set search_path=public,pg_temp as $$
declare uid uuid:=public.require_user_id(); range_start date; range_end date; previous_start date; previous_revenue numeric;
declare revenue numeric; sale_count integer; pieces integer; weight numeric; comparison numeric; trend jsonb; summary jsonb;
begin
 if p_period='weekly' then range_start:=date_trunc('week',p_selected_date)::date; range_end:=range_start+7; previous_start:=range_start-7;
 elsif p_period='monthly' then range_start:=date_trunc('month',p_selected_date)::date; range_end:=(range_start+interval '1 month')::date; previous_start:=(range_start-interval '1 month')::date;
 elsif p_period='yearly' then range_start:=date_trunc('year',p_selected_date)::date; range_end:=(range_start+interval '1 year')::date; previous_start:=(range_start-interval '1 year')::date;
 else range_start:=p_selected_date; range_end:=p_selected_date+1; previous_start:=p_selected_date-1; end if;
 select coalesce(sum(total_amount),0),count(*) into revenue,sale_count from public.sales where user_id=uid and archived_at is null and completed_at>=range_start and completed_at<range_end;
 select coalesce(sum(si.pieces),0),coalesce(sum(si.weight_kilograms),0) into pieces,weight from public.sale_items si join public.sales s on s.id=si.sale_id where s.user_id=uid and s.archived_at is null and s.completed_at>=range_start and s.completed_at<range_end;
 select coalesce(sum(total_amount),0) into previous_revenue from public.sales where user_id=uid and archived_at is null and completed_at>=previous_start and completed_at<range_start;
 comparison:=case when previous_revenue=0 then null else round(((revenue-previous_revenue)/previous_revenue)*100,2) end;
 select coalesce(jsonb_agg(jsonb_build_object('label',label,'revenue',bucket_revenue,'pieces',bucket_pieces) order by bucket),'[]'::jsonb) into trend from (
   select date_trunc(case when p_period='yearly' then 'month' else 'day' end,s.completed_at at time zone 'Asia/Manila') bucket,
          to_char(date_trunc(case when p_period='yearly' then 'month' else 'day' end,s.completed_at at time zone 'Asia/Manila'),case when p_period='yearly' then 'Mon' else 'MM-DD' end) label,
          sum(si.subtotal) bucket_revenue,sum(si.pieces) bucket_pieces
   from public.sales s join public.sale_items si on si.sale_id=s.id where s.user_id=uid and s.archived_at is null and s.completed_at>=range_start and s.completed_at<range_end group by 1,2) buckets;
 select coalesce(jsonb_agg(jsonb_build_object('size',size,'grade',grade,'pieces',category_pieces,'weightKilograms',category_weight,'revenue',category_revenue) order by grade,size),'[]'::jsonb) into summary from (
   select si.size,si.grade,sum(si.pieces) category_pieces,sum(si.weight_kilograms) category_weight,sum(si.subtotal) category_revenue
   from public.sale_items si join public.sales s on s.id=si.sale_id where s.user_id=uid and s.archived_at is null and s.completed_at>=range_start and s.completed_at<range_end group by si.size,si.grade) categories;
 return jsonb_build_object('period',p_period,'selectedDate',p_selected_date::text,'previousRevenue',previous_revenue,'comparisonPercent',comparison,
  'totals',jsonb_build_object('revenue',revenue,'completedSales',sale_count,'pieces',pieces,'weightKilograms',weight),'trend',trend,'summary',summary);
end;
$$;

revoke all on function public.inventory_transaction_json(uuid),public.planting_group_json(public.planting) from public,anon,authenticated;
revoke all on function public.get_inventory(),public.get_inventory_batches(),public.get_inventory_details(bigint),
 public.get_inventory_batch_details(text),public.get_prices(),public.get_sales(),public.get_planting(),public.get_planting_details(bigint),
 public.get_archived_records(),public.get_dashboard(),public.get_sales_analytics(text,date) from public,anon;
grant execute on function public.get_inventory(),public.get_inventory_batches(),public.get_inventory_details(bigint),
 public.get_inventory_batch_details(text),public.get_prices(),public.get_sales(),public.get_planting(),public.get_planting_details(bigint),
 public.get_archived_records(),public.get_dashboard(),public.get_sales_analytics(text,date) to authenticated;
