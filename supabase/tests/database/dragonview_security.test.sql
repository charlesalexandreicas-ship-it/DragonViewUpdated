begin;
create extension if not exists pgtap with schema extensions;
select plan(22);

insert into auth.users(instance_id,id,aud,role,email,encrypted_password,email_confirmed_at,
 raw_app_meta_data,raw_user_meta_data,created_at,updated_at)
values
 ('00000000-0000-0000-0000-000000000000','10000000-0000-0000-0000-000000000001','authenticated','authenticated',
  'owner-a@example.test',extensions.crypt('test-password',extensions.gen_salt('bf')),now(),'{"provider":"email","providers":["email"]}',
  '{"display_name":"Owner A"}',now(),now()),
 ('00000000-0000-0000-0000-000000000000','20000000-0000-0000-0000-000000000002','authenticated','authenticated',
  'owner-b@example.test',extensions.crypt('test-password',extensions.gen_salt('bf')),now(),'{"provider":"email","providers":["email"]}',
 '{"display_name":"Owner B"}',now(),now());

update public.workspaces set invite_code='OWNERA01'
where owner_id='10000000-0000-0000-0000-000000000001';

set local role authenticated;
select set_config('request.jwt.claim.sub','10000000-0000-0000-0000-000000000001',true);
select set_config('request.jwt.claims','{"sub":"10000000-0000-0000-0000-000000000001","role":"authenticated"}',true);

select is((select count(*) from public.profiles),1::bigint,'RLS exposes only the signed-in profile');
select is((select count(*) from public.prices),11::bigint,'new profile receives eleven default prices');
select is(jsonb_array_length(public.get_workspaces()),1,'new profile receives one starter workspace');
select is(public.get_workspaces()->0->>'role','OWNER','starter workspace grants owner role');
select throws_ok(
 $$insert into public.inventory(user_id,batch_number,harvest_date,size,grade,original_pieces,available_pieces)
   values('10000000-0000-0000-0000-000000000001','FORGED','2026-09-08','LARGE','A',1,1)$$,
 '42501',null,'authenticated clients cannot bypass RPC write grants');

select lives_ok(
 $$select public.register_harvest('30000000-0000-0000-0000-000000000003',
   '{"batchNumber":"B-TEST","harvestDate":"2026-09-08","items":[{"size":"LARGE","grade":"A","pieces":10}]}'::jsonb)$$,
 'owner can register a harvest through RPC');
select is((select count(*) from public.inventory),1::bigint,'harvest creates one inventory row');
select public.register_harvest('30000000-0000-0000-0000-000000000003',
 '{"batchNumber":"B-TEST","harvestDate":"2026-09-08","items":[{"size":"LARGE","grade":"A","pieces":10}]}'::jsonb);
select is((select count(*) from public.inventory),1::bigint,'repeated operation ID is idempotent');

select set_config('request.jwt.claim.sub','20000000-0000-0000-0000-000000000002',true);
select set_config('request.jwt.claims','{"sub":"20000000-0000-0000-0000-000000000002","role":"authenticated"}',true);
select is((select count(*) from public.inventory),0::bigint,'second account cannot see the first account inventory');
select lives_ok($$select public.join_workspace('OWNERA01')$$,
 'farmer can join an owner workspace with its invite code');
select is((select count(*) from public.inventory),1::bigint,
 'farmer can read inventory in the joined active workspace');
select is(public.get_workspaces()->0->>'role','FARMER',
 'joined workspace assigns the farmer role');
select throws_ok(
 $$select public.configure_price('50000000-0000-0000-0000-000000000005',
   '{"grade":"A","size":"LARGE","value":125}'::jsonb)$$,
 '42501',null,'farmer cannot configure owner-only prices');

select set_config('request.jwt.claim.sub','10000000-0000-0000-0000-000000000001',true);
select set_config('request.jwt.claims','{"sub":"10000000-0000-0000-0000-000000000001","role":"authenticated"}',true);
select throws_ok(
 $$select public.complete_sale('40000000-0000-0000-0000-000000000004',
 '{"customer":{"name":"Buyer","address":"","contactNumber":"","emailAddress":""},
   "items":[{"size":"LARGE","grade":"A","pieces":99,"totalWeightKilograms":"10.000","inventoryId":null}],
   "payment":{"method":"CASH","amountPaid":"2000","reference":null,"otherEWalletProvider":null},
   "saleMode":"SELECTED_ITEMS","batchNumber":null}'::jsonb)$$,
 'P0001',null,'insufficient-stock sale is rejected');
select is((select available_pieces from public.inventory where batch_number='B-TEST'),10,
 'failed sale rolls back without changing inventory');

select set_config('request.jwt.claim.sub','20000000-0000-0000-0000-000000000002',true);
select set_config('request.jwt.claims','{"sub":"20000000-0000-0000-0000-000000000002","role":"authenticated"}',true);
select throws_ok(
 $$select public.delete_workspace()$$,
 '42501',null,'farmer cannot delete an owner workspace');

select set_config('request.jwt.claim.sub','10000000-0000-0000-0000-000000000001',true);
select set_config('request.jwt.claims','{"sub":"10000000-0000-0000-0000-000000000001","role":"authenticated"}',true);
select lives_ok($$select public.delete_workspace()$$,
 'owner can soft-delete the active workspace');
select is(jsonb_array_length(public.get_workspaces()),0,
 'deleted workspace is hidden from the owner workspace list');
select ok((select active_workspace_id is null from public.profiles
 where id='10000000-0000-0000-0000-000000000001'),
 'deleted workspace is cleared as the owner active workspace');

select set_config('request.jwt.claim.sub','20000000-0000-0000-0000-000000000002',true);
select set_config('request.jwt.claims','{"sub":"20000000-0000-0000-0000-000000000002","role":"authenticated"}',true);
select throws_ok($$select public.join_workspace('OWNERA01')$$,
 'P0001','Invite code is invalid','deleted workspace invite code is disabled');

reset role;
select is((select count(*) from public.inventory where batch_number='B-TEST'),1::bigint,
 'soft deletion preserves workspace inventory records');
select ok((select deleted_at is not null from public.workspaces
 where owner_id='10000000-0000-0000-0000-000000000001'),
 'soft-deleted workspace records the deletion timestamp');

select * from finish();
rollback;
