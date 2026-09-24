create or replace function public.register_harvest(p_operation_id uuid, p_payload jsonb)
returns jsonb language plpgsql security definer set search_path = public, pg_temp as $$
declare uid uuid := public.require_user_id();
declare prior jsonb; item jsonb; inventory_uuid uuid; actor text; result jsonb;
begin
    prior := public.previous_request(p_operation_id, 'register_harvest');
    if prior is not null then return prior; end if;
    if coalesce(trim(p_payload->>'batchNumber'), '') = '' then raise exception 'Batch number is required'; end if;
    if jsonb_array_length(coalesce(p_payload->'items','[]'::jsonb)) = 0 then raise exception 'Add at least one harvest item'; end if;
    select display_name into actor from public.profiles where id = uid;
    for item in select * from jsonb_array_elements(p_payload->'items') loop
        if (item->>'pieces')::integer < 1 then raise exception 'Pieces must be greater than zero'; end if;
        insert into public.inventory(user_id,batch_number,harvest_date,size,grade,original_pieces,available_pieces)
        values(uid,trim(p_payload->>'batchNumber'),(p_payload->>'harvestDate')::date,
               item->>'size',item->>'grade',(item->>'pieces')::integer,(item->>'pieces')::integer)
        returning id into inventory_uuid;
        insert into public.inventory_transactions(user_id,inventory_id,type,pieces,remarks,created_by)
        values(uid,inventory_uuid,'HARVEST_IN',(item->>'pieces')::integer,'Initial harvest registration',actor);
    end loop;
    result := jsonb_build_object('ok',true);
    return public.remember_request(p_operation_id,'register_harvest',result);
end;
$$;

create or replace function public.adjust_inventory(p_operation_id uuid, p_payload jsonb)
returns jsonb language plpgsql security definer set search_path = public, pg_temp as $$
declare uid uuid := public.require_user_id(); prior jsonb; target public.inventory%rowtype;
declare delta integer := (p_payload->>'pieces')::integer; actor text; result jsonb;
begin
    prior := public.previous_request(p_operation_id,'adjust_inventory'); if prior is not null then return prior; end if;
    select * into target from public.inventory where user_id=uid and display_id=(p_payload->>'inventoryId')::bigint and archived_at is null for update;
    if not found then raise exception 'Inventory not found'; end if;
    if target.available_pieces + delta < 0 then raise exception 'Adjustment exceeds available inventory'; end if;
    select display_name into actor from public.profiles where id=uid;
    update public.inventory set available_pieces=available_pieces+delta where id=target.id;
    insert into public.inventory_transactions(user_id,inventory_id,type,pieces,remarks,created_by)
    values(uid,target.id,'MANUAL_ADJUSTMENT',delta,nullif(trim(p_payload->>'reason'),''),actor);
    result:=jsonb_build_object('ok',true); return public.remember_request(p_operation_id,'adjust_inventory',result);
end;
$$;

create or replace function public.regrade_inventory(p_operation_id uuid, p_payload jsonb)
returns jsonb language plpgsql security definer set search_path = public, pg_temp as $$
declare uid uuid := public.require_user_id(); prior jsonb; source public.inventory%rowtype; target public.inventory%rowtype;
declare quantity integer := (p_payload->>'pieces')::integer; target_grade text := p_payload->>'targetGrade'; actor text; result jsonb;
begin
    prior:=public.previous_request(p_operation_id,'regrade_inventory'); if prior is not null then return prior; end if;
    select * into source from public.inventory where user_id=uid and display_id=(p_payload->>'inventoryId')::bigint and archived_at is null for update;
    if not found then raise exception 'Inventory not found'; end if;
    if source.grade=target_grade or target_grade not in ('A','B','C') then raise exception 'Invalid target grade'; end if;
    if quantity < 1 or quantity > source.available_pieces then raise exception 'Regrading exceeds available inventory'; end if;
    select * into target from public.inventory where user_id=uid and batch_number=source.batch_number and size=source.size and grade=target_grade for update;
    if found then
        update public.inventory set original_pieces=original_pieces+quantity,available_pieces=available_pieces+quantity where id=target.id;
    else
        insert into public.inventory(user_id,batch_number,harvest_date,size,grade,original_pieces,available_pieces)
        values(uid,source.batch_number,source.harvest_date,source.size,target_grade,quantity,quantity) returning * into target;
    end if;
    update public.inventory set available_pieces=available_pieces-quantity where id=source.id;
    select display_name into actor from public.profiles where id=uid;
    insert into public.inventory_transactions(user_id,inventory_id,type,pieces,remarks,created_by) values
      (uid,source.id,'REGRADING_OUT',-quantity,nullif(trim(p_payload->>'reason'),''),actor),
      (uid,target.id,'REGRADING_IN',quantity,nullif(trim(p_payload->>'reason'),''),actor);
    result:=jsonb_build_object('ok',true); return public.remember_request(p_operation_id,'regrade_inventory',result);
end;
$$;

create or replace function public.configure_price(p_operation_id uuid, p_payload jsonb)
returns jsonb language plpgsql security definer set search_path = public, pg_temp as $$
declare uid uuid := public.require_user_id(); prior jsonb; normalized_size text; result jsonb;
begin
    prior:=public.previous_request(p_operation_id,'configure_price'); if prior is not null then return prior; end if;
    normalized_size:=case when p_payload->>'grade'='C' then null else p_payload->>'size' end;
    if (p_payload->>'value')::numeric < 0 then raise exception 'Price cannot be negative'; end if;
    update public.prices set active=false where user_id=uid and grade=p_payload->>'grade' and size is not distinct from normalized_size and active;
    insert into public.prices(user_id,grade,size,price_per_kilogram)
    values(uid,p_payload->>'grade',normalized_size,(p_payload->>'value')::numeric);
    result:=jsonb_build_object('ok',true); return public.remember_request(p_operation_id,'configure_price',result);
end;
$$;

create or replace function public.create_planting(p_operation_id uuid, p_payload jsonb)
returns jsonb language plpgsql security definer set search_path = public, pg_temp as $$
declare uid uuid:=public.require_user_id(); prior jsonb; planting_uuid uuid; method text; initial_stage text; result jsonb;
begin
    prior:=public.previous_request(p_operation_id,'create_planting'); if prior is not null then return prior; end if;
    method:=coalesce(p_payload->>'propagationMethod','STEM_CUTTING');
    initial_stage:=case method when 'SEED' then 'SEED_SOWING' when 'GRAFTED' then 'GRAFT_PREPARATION' else 'PLANTED' end;
    insert into public.planting(user_id,record_number,grafting_date,variety,location,number_of_plants,propagation_method,cutting_type,current_stage)
    values(uid,trim(p_payload->>'recordNumber'),(p_payload->>'graftingDate')::date,trim(p_payload->>'variety'),trim(p_payload->>'location'),
           (p_payload->>'numberOfPlants')::integer,method,coalesce(nullif(p_payload->>'cuttingType',''),'UNROOTED'),initial_stage)
    returning id into planting_uuid;
    insert into public.planting_updates(user_id,planting_id,type,stage,note,recorded_date)
    values(uid,planting_uuid,'MILESTONE',initial_stage,replace(initcap(lower(method)),'_',' ')||' record created.',(p_payload->>'graftingDate')::date);
    result:=jsonb_build_object('ok',true); return public.remember_request(p_operation_id,'create_planting',result);
end;
$$;

create or replace function public.update_planting_stage(p_operation_id uuid, p_payload jsonb)
returns jsonb language plpgsql security definer set search_path = public, pg_temp as $$
declare uid uuid:=public.require_user_id(); prior jsonb; planting_uuid uuid; result jsonb;
begin
    prior:=public.previous_request(p_operation_id,'update_planting_stage'); if prior is not null then return prior; end if;
    select id into planting_uuid from public.planting where user_id=uid and display_id=(p_payload->>'plantingId')::bigint and archived_at is null for update;
    if not found then raise exception 'Planting record not found'; end if;
    update public.planting set current_stage=p_payload->>'stage',flowering_date=case when p_payload->>'stage'='FLOWERING' then (p_payload->>'recordedDate')::date else flowering_date end where id=planting_uuid;
    insert into public.planting_updates(user_id,planting_id,type,stage,note,recorded_date)
    values(uid,planting_uuid,'MILESTONE',p_payload->>'stage',coalesce(nullif(trim(p_payload->>'note'),''),'Stage confirmed: '||replace(initcap(lower(p_payload->>'stage')),'_',' ')),(p_payload->>'recordedDate')::date);
    result:=jsonb_build_object('ok',true); return public.remember_request(p_operation_id,'update_planting_stage',result);
end;
$$;

create or replace function public.record_grafting(p_operation_id uuid, p_payload jsonb)
returns jsonb language plpgsql security definer set search_path = public, pg_temp as $$
declare uid uuid:=public.require_user_id(); prior jsonb; target public.planting%rowtype; result jsonb;
begin
    prior:=public.previous_request(p_operation_id,'record_grafting'); if prior is not null then return prior; end if;
    select * into target from public.planting where user_id=uid and display_id=(p_payload->>'plantingId')::bigint and archived_at is null for update;
    if not found then raise exception 'Planting record not found'; end if;
    if target.propagation_method <> 'SEED' then raise exception 'Only a seed-grown record can enter the grafting pathway'; end if;
    insert into public.grafting_events(user_id,planting_id,grafting_date,scion_variety,note)
    values(uid,target.id,(p_payload->>'graftingDate')::date,trim(p_payload->>'scionVariety'),nullif(trim(p_payload->>'note'),''));
    update public.planting set propagation_method='GRAFTED',current_stage='GRAFT_UNION',variety=trim(p_payload->>'scionVariety') where id=target.id;
    insert into public.planting_updates(user_id,planting_id,type,stage,note,recorded_date)
    values(uid,target.id,'GRAFTING','GRAFT_UNION',coalesce(nullif(trim(p_payload->>'note'),''),'Grafting recorded.'),(p_payload->>'graftingDate')::date);
    result:=jsonb_build_object('ok',true); return public.remember_request(p_operation_id,'record_grafting',result);
end;
$$;

create or replace function public.add_planting_update(p_operation_id uuid, p_payload jsonb)
returns jsonb language plpgsql security definer set search_path = public, pg_temp as $$
declare uid uuid:=public.require_user_id(); prior jsonb; target public.planting%rowtype; result jsonb;
begin
    prior:=public.previous_request(p_operation_id,'add_planting_update'); if prior is not null then return prior; end if;
    select * into target from public.planting where user_id=uid and display_id=(p_payload->>'plantingId')::bigint and archived_at is null for update;
    if not found then raise exception 'Planting record not found'; end if;
    if nullif(trim(p_payload->>'note'),'') is null and p_payload->>'measurementCentimeters' is null and p_payload->>'photoUri' is null then
        raise exception 'Add a note, measurement, or progress photo';
    end if;
    insert into public.planting_updates(user_id,planting_id,type,stage,note,measurement_centimeters,photo_uri,recorded_date)
    values(uid,target.id,case when p_payload->>'photoUri' is null then 'OBSERVATION' else 'PHOTO' end,target.current_stage,
           nullif(trim(p_payload->>'note'),''),nullif(p_payload->>'measurementCentimeters','')::numeric,p_payload->>'photoUri',(p_payload->>'recordedDate')::date);
    update public.planting set updated_at=now() where id=target.id;
    result:=jsonb_build_object('ok',true); return public.remember_request(p_operation_id,'add_planting_update',result);
end;
$$;

create or replace function public.archive_record(p_operation_id uuid, p_payload jsonb)
returns jsonb language plpgsql security definer set search_path = public, pg_temp as $$
declare uid uuid:=public.require_user_id(); prior jsonb; kind text:=p_payload->>'type'; key text:=p_payload->>'key'; affected integer; title text; result jsonb;
begin
    prior:=public.previous_request(p_operation_id,'archive_record'); if prior is not null then return prior; end if;
    if kind='INVENTORY_BATCH' then
        update public.inventory set archived_at=now(),archive_reason=nullif(trim(p_payload->>'reason'),'') where user_id=uid and batch_number=key and archived_at is null;
        get diagnostics affected=row_count; title:='Batch #'||key;
    elsif kind='SALE' then
        update public.sales set archived_at=now(),archive_reason=nullif(trim(p_payload->>'reason'),'') where user_id=uid and display_id=key::bigint and archived_at is null;
        get diagnostics affected=row_count; title:='Sale #'||key;
    elsif kind='PLANTING' then
        update public.planting set archived_at=now(),archive_reason=nullif(trim(p_payload->>'reason'),'') where user_id=uid and display_id=key::bigint and archived_at is null;
        get diagnostics affected=row_count; title:='Planting record #'||key;
    else raise exception 'Unsupported record type'; end if;
    if affected=0 then raise exception 'Record not found or already archived'; end if;
    insert into public.record_archive_events(user_id,record_type,record_key,record_title,action,reason)
    values(uid,kind,key,title,'ARCHIVED',nullif(trim(p_payload->>'reason'),''));
    result:=jsonb_build_object('ok',true); return public.remember_request(p_operation_id,'archive_record',result);
end;
$$;

create or replace function public.restore_record(p_operation_id uuid, p_payload jsonb)
returns jsonb language plpgsql security definer set search_path = public, pg_temp as $$
declare uid uuid:=public.require_user_id(); prior jsonb; kind text:=p_payload->>'type'; key text:=p_payload->>'key'; affected integer; title text; result jsonb;
begin
    prior:=public.previous_request(p_operation_id,'restore_record'); if prior is not null then return prior; end if;
    if kind='INVENTORY_BATCH' then
        update public.inventory set archived_at=null,archive_reason=null where user_id=uid and batch_number=key and archived_at is not null;
        get diagnostics affected=row_count; title:='Batch #'||key;
    elsif kind='SALE' then
        update public.sales set archived_at=null,archive_reason=null where user_id=uid and display_id=key::bigint and archived_at is not null;
        get diagnostics affected=row_count; title:='Sale #'||key;
    elsif kind='PLANTING' then
        update public.planting set archived_at=null,archive_reason=null where user_id=uid and display_id=key::bigint and archived_at is not null;
        get diagnostics affected=row_count; title:='Planting record #'||key;
    else raise exception 'Unsupported record type'; end if;
    if affected=0 then raise exception 'Record is no longer archived'; end if;
    insert into public.record_archive_events(user_id,record_type,record_key,record_title,action)
    values(uid,kind,key,title,'RESTORED');
    result:=jsonb_build_object('ok',true); return public.remember_request(p_operation_id,'restore_record',result);
end;
$$;

create or replace function public.complete_sale(p_operation_id uuid, p_payload jsonb)
returns jsonb language plpgsql security definer set search_path = public, pg_temp as $$
declare uid uuid:=public.require_user_id(); prior jsonb; sale_uuid uuid; item jsonb; lot public.inventory%rowtype;
declare remaining integer; take_count integer; unit_price numeric(12,2); subtotal numeric(12,2); total numeric(12,2):=0;
declare amount_paid numeric(12,2); actor text; result jsonb; fifo_override boolean:=false; change_amount numeric(12,2);
begin
    prior:=public.previous_request(p_operation_id,'complete_sale'); if prior is not null then return prior; end if;
    if jsonb_array_length(coalesce(p_payload->'items','[]'::jsonb))=0 then raise exception 'Add at least one sale item'; end if;
    select display_name into actor from public.profiles where id=uid;
    -- Lock all candidate stock in deterministic FIFO order before validating or deducting.
    perform 1 from public.inventory where user_id=uid and archived_at is null and available_pieces>0 order by harvest_date,display_id for update;
    if coalesce(p_payload->>'saleMode','SELECTED_ITEMS')='ENTIRE_BATCH' then
        if coalesce(trim(p_payload->>'batchNumber'),'')='' then raise exception 'Select a batch to sell'; end if;
        if (select count(*) from public.inventory where user_id=uid and batch_number=p_payload->>'batchNumber' and archived_at is null and available_pieces>0)
              <> jsonb_array_length(p_payload->'items') then
            raise exception 'The batch changed. Reload it before completing the sale';
        end if;
    end if;
    for item in select * from jsonb_array_elements(p_payload->'items') loop
        remaining:=(item->>'pieces')::integer;
        if remaining<1 or (item->>'totalWeightKilograms')::numeric<=0 then raise exception 'Sale item values are invalid'; end if;
        select price_per_kilogram into unit_price from public.prices
        where user_id=uid and grade=item->>'grade' and active and
              ((item->>'grade'='C' and size is null) or size=item->>'size') order by effective_at desc limit 1;
        if unit_price is null then raise exception 'No active price is configured for %/%',item->>'grade',item->>'size'; end if;
        if coalesce(p_payload->>'saleMode','SELECTED_ITEMS')='ENTIRE_BATCH' then
            if item->>'inventoryId' is null then raise exception 'Whole-batch inventory identifier is required'; end if;
            select * into lot from public.inventory where user_id=uid and display_id=(item->>'inventoryId')::bigint and batch_number=p_payload->>'batchNumber' and archived_at is null;
            if not found or lot.available_pieces<>remaining or lot.size<>item->>'size' or lot.grade<>item->>'grade' then raise exception 'The batch changed. Reload it before completing the sale'; end if;
            if exists(select 1 from public.inventory older where older.user_id=uid and older.archived_at is null and older.available_pieces>0
                      and older.size=lot.size and older.grade=lot.grade and (older.harvest_date,older.display_id)<(lot.harvest_date,lot.display_id)) then
                fifo_override:=true;
            end if;
        else
            if (select coalesce(sum(available_pieces),0) from public.inventory where user_id=uid and archived_at is null and size=item->>'size' and grade=item->>'grade') < remaining then
                raise exception 'Insufficient inventory for %/%',item->>'grade',item->>'size';
            end if;
        end if;
        total:=total+round((item->>'totalWeightKilograms')::numeric*unit_price,2);
    end loop;
    amount_paid:=(p_payload->'payment'->>'amountPaid')::numeric;
    if p_payload->'payment'->>'method'='CASH' then
        if amount_paid+0.005<total then raise exception 'Payment amount is invalid'; end if;
        change_amount:=amount_paid-total;
    else
        if abs(amount_paid-total)>0.009 then raise exception 'Payment amount is invalid'; end if;
        change_amount:=0;
    end if;
    if fifo_override and coalesce(trim(p_payload->>'fifoOverrideReason'),'')='' then
        raise exception 'Explain why older matching stock is being skipped';
    end if;
    insert into public.sales(user_id,customer_name,customer_address,customer_contact,customer_email,payment_method,amount_paid,total_amount,change_due,payment_reference,provider)
    values(uid,coalesce(p_payload->'customer'->>'name',''),coalesce(p_payload->'customer'->>'address',''),coalesce(p_payload->'customer'->>'contactNumber',''),
           coalesce(p_payload->'customer'->>'emailAddress',''),p_payload->'payment'->>'method',amount_paid,total,change_amount,
           nullif(p_payload->'payment'->>'reference',''),nullif(p_payload->'payment'->>'otherEWalletProvider','')) returning id into sale_uuid;
    for item in select * from jsonb_array_elements(p_payload->'items') loop
        remaining:=(item->>'pieces')::integer;
        select price_per_kilogram into unit_price from public.prices where user_id=uid and grade=item->>'grade' and active and ((item->>'grade'='C' and size is null) or size=item->>'size') order by effective_at desc limit 1;
        subtotal:=round((item->>'totalWeightKilograms')::numeric*unit_price,2);
        insert into public.sale_items(user_id,sale_id,size,grade,pieces,weight_kilograms,price_per_kilogram,subtotal)
        values(uid,sale_uuid,item->>'size',item->>'grade',remaining,(item->>'totalWeightKilograms')::numeric,unit_price,subtotal);
        for lot in select * from public.inventory where user_id=uid and archived_at is null and available_pieces>0 and size=item->>'size' and grade=item->>'grade'
                   and (coalesce(p_payload->>'saleMode','SELECTED_ITEMS')<>'ENTIRE_BATCH' or display_id=(item->>'inventoryId')::bigint)
                   order by harvest_date,display_id loop
            exit when remaining=0; take_count:=least(remaining,lot.available_pieces);
            update public.inventory set available_pieces=available_pieces-take_count where id=lot.id;
            insert into public.inventory_transactions(user_id,inventory_id,type,pieces,remarks,created_by)
            values(uid,lot.id,'SALE_OUT',-take_count,'Sale #'||(select display_id from public.sales where id=sale_uuid)||
              case when coalesce(p_payload->>'saleMode','SELECTED_ITEMS')='ENTIRE_BATCH' then ' • Entire batch '||(p_payload->>'batchNumber')||
                case when fifo_override then ' • FIFO override: '||(p_payload->>'fifoOverrideReason') else '' end else '' end,actor);
            remaining:=remaining-take_count;
        end loop;
        if remaining<>0 then raise exception 'Inventory changed while completing the sale'; end if;
    end loop;
    result:=jsonb_build_object('ok',true,'saleId',(select display_id from public.sales where id=sale_uuid));
    return public.remember_request(p_operation_id,'complete_sale',result);
end;
$$;

revoke all on all functions in schema public from public, anon;
grant execute on function public.register_harvest(uuid,jsonb), public.adjust_inventory(uuid,jsonb),
 public.regrade_inventory(uuid,jsonb), public.configure_price(uuid,jsonb), public.create_planting(uuid,jsonb),
 public.update_planting_stage(uuid,jsonb), public.record_grafting(uuid,jsonb), public.add_planting_update(uuid,jsonb),
 public.archive_record(uuid,jsonb), public.restore_record(uuid,jsonb), public.complete_sale(uuid,jsonb) to authenticated;
