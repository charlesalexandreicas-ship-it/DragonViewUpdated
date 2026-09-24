create extension if not exists pgcrypto with schema extensions;

create table public.profiles (
    id uuid primary key references auth.users(id) on delete cascade,
    email text not null,
    display_name text not null check (length(trim(display_name)) >= 2),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    version bigint not null default 1
);

create table public.api_requests (
    id uuid primary key,
    user_id uuid not null references public.profiles(id) on delete cascade,
    operation text not null,
    response jsonb,
    created_at timestamptz not null default now(),
    unique (id, user_id, operation)
);

create table public.inventory (
    id uuid primary key default extensions.gen_random_uuid(),
    display_id bigint generated always as identity unique,
    user_id uuid not null references public.profiles(id) on delete cascade,
    batch_number text not null check (length(trim(batch_number)) > 0),
    harvest_date date not null,
    size text not null check (size in ('EXTRA_SMALL','SMALL','MEDIUM','LARGE','JUMBO')),
    grade text not null check (grade in ('A','B','C')),
    original_pieces integer not null check (original_pieces >= 0),
    available_pieces integer not null check (available_pieces >= 0),
    archived_at timestamptz,
    archive_reason text,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    version bigint not null default 1,
    unique (user_id, batch_number, size, grade)
);

create table public.inventory_transactions (
    id uuid primary key default extensions.gen_random_uuid(),
    display_id bigint generated always as identity unique,
    user_id uuid not null references public.profiles(id) on delete cascade,
    inventory_id uuid not null references public.inventory(id) on delete cascade,
    type text not null,
    pieces integer not null,
    remarks text,
    created_by text not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    version bigint not null default 1
);

create table public.prices (
    id uuid primary key default extensions.gen_random_uuid(),
    display_id bigint generated always as identity unique,
    user_id uuid not null references public.profiles(id) on delete cascade,
    grade text not null check (grade in ('A','B','C')),
    size text,
    price_per_kilogram numeric(12,2) not null check (price_per_kilogram >= 0),
    active boolean not null default true,
    effective_at timestamptz not null default now(),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    version bigint not null default 1,
    check ((grade = 'C' and size is null) or
           (grade <> 'C' and size in ('EXTRA_SMALL','SMALL','MEDIUM','LARGE','JUMBO')))
);

create unique index prices_one_active_category
    on public.prices(user_id, grade, coalesce(size, '')) where active;

create table public.sales (
    id uuid primary key default extensions.gen_random_uuid(),
    display_id bigint generated always as identity unique,
    user_id uuid not null references public.profiles(id) on delete cascade,
    customer_name text not null,
    customer_address text not null default '',
    customer_contact text not null default '',
    customer_email text not null default '',
    status text not null default 'COMPLETED',
    payment_status text not null default 'PAID',
    payment_method text not null,
    amount_paid numeric(12,2) not null check (amount_paid >= 0),
    total_amount numeric(12,2) not null check (total_amount >= 0),
    change_due numeric(12,2) not null check (change_due >= 0),
    payment_reference text,
    provider text,
    completed_at timestamptz not null default now(),
    archived_at timestamptz,
    archive_reason text,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    version bigint not null default 1
);

create table public.sale_items (
    id uuid primary key default extensions.gen_random_uuid(),
    display_id bigint generated always as identity unique,
    user_id uuid not null references public.profiles(id) on delete cascade,
    sale_id uuid not null references public.sales(id) on delete cascade,
    size text not null,
    grade text not null,
    pieces integer not null check (pieces > 0),
    weight_kilograms numeric(12,3) not null check (weight_kilograms > 0),
    price_per_kilogram numeric(12,2) not null check (price_per_kilogram >= 0),
    subtotal numeric(12,2) not null check (subtotal >= 0),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    version bigint not null default 1
);

create table public.planting (
    id uuid primary key default extensions.gen_random_uuid(),
    display_id bigint generated always as identity unique,
    user_id uuid not null references public.profiles(id) on delete cascade,
    record_number text not null,
    grafting_date date not null,
    variety text not null,
    location text not null,
    propagation_method text not null default 'STEM_CUTTING'
        check (propagation_method in ('SEED','STEM_CUTTING','GRAFTED')),
    cutting_type text not null default 'UNROOTED',
    current_stage text not null default 'PLANTED',
    flowering_date date,
    number_of_plants integer not null check (number_of_plants > 0),
    archived_at timestamptz,
    archive_reason text,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    version bigint not null default 1,
    unique (user_id, record_number)
);

create table public.grafting_events (
    id uuid primary key default extensions.gen_random_uuid(),
    display_id bigint generated always as identity unique,
    user_id uuid not null references public.profiles(id) on delete cascade,
    planting_id uuid not null references public.planting(id) on delete cascade,
    grafting_date date not null,
    scion_variety text not null,
    note text,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    version bigint not null default 1
);

create table public.planting_updates (
    id uuid primary key default extensions.gen_random_uuid(),
    display_id bigint generated always as identity unique,
    user_id uuid not null references public.profiles(id) on delete cascade,
    planting_id uuid not null references public.planting(id) on delete cascade,
    type text not null default 'OBSERVATION',
    stage text,
    note text,
    measurement_centimeters numeric(10,2),
    photo_uri text,
    recorded_date date not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    version bigint not null default 1
);

create table public.record_archive_events (
    id uuid primary key default extensions.gen_random_uuid(),
    display_id bigint generated always as identity unique,
    user_id uuid not null references public.profiles(id) on delete cascade,
    record_type text not null check (record_type in ('PLANTING','INVENTORY_BATCH','SALE')),
    record_key text not null,
    record_title text not null,
    action text not null check (action in ('ARCHIVED','RESTORED')),
    reason text,
    event_at timestamptz not null default now(),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    version bigint not null default 1
);

create index inventory_user_fifo on public.inventory(user_id, size, grade, harvest_date, display_id)
    where archived_at is null and available_pieces > 0;
create index inventory_user_batch on public.inventory(user_id, batch_number);
create index inventory_transactions_parent on public.inventory_transactions(user_id, inventory_id, created_at);
create index prices_user_active on public.prices(user_id, grade, size) where active;
create index sales_user_completed on public.sales(user_id, completed_at desc);
create index sale_items_parent on public.sale_items(user_id, sale_id);
create index planting_user_active on public.planting(user_id, grafting_date, display_id) where archived_at is null;
create index planting_updates_parent on public.planting_updates(user_id, planting_id, recorded_date desc);
create index grafting_events_parent on public.grafting_events(user_id, planting_id, grafting_date desc);
create index archive_events_user on public.record_archive_events(user_id, event_at desc);

create or replace function public.touch_version()
returns trigger language plpgsql set search_path = public, pg_temp as $$
begin
    new.updated_at = now();
    new.version = old.version + 1;
    return new;
end;
$$;

do $$
declare table_name text;
begin
    foreach table_name in array array[
        'profiles','inventory','inventory_transactions','prices','sales','sale_items',
        'planting','grafting_events','planting_updates','record_archive_events'
    ] loop
        execute format('create trigger touch_%I before update on public.%I for each row execute function public.touch_version()', table_name, table_name);
    end loop;
end $$;

create or replace function public.handle_new_user()
returns trigger language plpgsql security definer set search_path = public, pg_temp as $$
declare sizes text[] := array['EXTRA_SMALL','SMALL','MEDIUM','LARGE','JUMBO'];
declare grade_a numeric[] := array[70,80,100,120,140];
declare grade_b numeric[] := array[60,70,85,100,115];
declare i integer;
declare profile_name text;
begin
    profile_name:=coalesce(nullif(trim(new.raw_user_meta_data->>'display_name'), ''), split_part(coalesce(new.email,''), '@', 1));
    if length(profile_name)<2 then profile_name:='Farm User'; end if;
    insert into public.profiles(id, email, display_name)
    values (new.id, coalesce(new.email, ''), profile_name);
    for i in 1..5 loop
        insert into public.prices(user_id, grade, size, price_per_kilogram)
        values (new.id, 'A', sizes[i], grade_a[i]), (new.id, 'B', sizes[i], grade_b[i]);
    end loop;
    insert into public.prices(user_id, grade, size, price_per_kilogram)
    values (new.id, 'C', null, 50);
    return new;
end;
$$;

create trigger on_auth_user_created after insert on auth.users
for each row execute function public.handle_new_user();

alter table public.profiles enable row level security;
alter table public.api_requests enable row level security;
alter table public.inventory enable row level security;
alter table public.inventory_transactions enable row level security;
alter table public.prices enable row level security;
alter table public.sales enable row level security;
alter table public.sale_items enable row level security;
alter table public.planting enable row level security;
alter table public.grafting_events enable row level security;
alter table public.planting_updates enable row level security;
alter table public.record_archive_events enable row level security;

create policy profiles_select_own on public.profiles for select to authenticated
    using (auth.uid() is not null and id = auth.uid());
create policy api_requests_select_own on public.api_requests for select to authenticated
    using (auth.uid() is not null and user_id = auth.uid());

do $$
declare table_name text;
begin
    foreach table_name in array array[
        'inventory','inventory_transactions','prices','sales','sale_items','planting',
        'grafting_events','planting_updates','record_archive_events'
    ] loop
        execute format('create policy %I on public.%I for select to authenticated using (auth.uid() is not null and user_id = auth.uid())', table_name || '_select_own', table_name);
    end loop;
end $$;

revoke all on all tables in schema public from anon, authenticated;
grant select on public.profiles, public.inventory, public.inventory_transactions,
    public.prices, public.sales, public.sale_items, public.planting,
    public.grafting_events, public.planting_updates, public.record_archive_events
    to authenticated;

create or replace function public.require_user_id()
returns uuid language plpgsql stable security invoker set search_path = public, pg_temp as $$
declare result uuid := auth.uid();
begin
    if result is null then raise exception 'Authentication required' using errcode = '28000'; end if;
    return result;
end;
$$;

create or replace function public.previous_request(p_id uuid, p_operation text)
returns jsonb language plpgsql volatile security definer set search_path = public, pg_temp as $$
declare uid uuid:=public.require_user_id(); result jsonb;
begin
    insert into public.api_requests(id,user_id,operation,response)
    values(p_id,uid,p_operation,null) on conflict(id) do nothing;
    select response into result from public.api_requests
    where id=p_id and user_id=uid and operation=p_operation for update;
    if not found then raise exception 'Operation identifier was already used for another request'; end if;
    return result;
end;
$$;

create or replace function public.remember_request(p_id uuid, p_operation text, p_response jsonb)
returns jsonb language plpgsql security definer set search_path = public, pg_temp as $$
begin
    update public.api_requests set response=p_response
    where id=p_id and user_id=public.require_user_id() and operation=p_operation;
    if not found then raise exception 'Operation identifier was not claimed'; end if;
    return p_response;
end;
$$;

revoke all on function public.require_user_id() from public, anon, authenticated;
revoke all on function public.previous_request(uuid,text) from public, anon, authenticated;
revoke all on function public.remember_request(uuid,text,jsonb) from public, anon, authenticated;
