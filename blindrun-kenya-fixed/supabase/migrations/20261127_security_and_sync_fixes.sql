-- =====================================================================
-- BlindRun Kenya — security & sync fixes (2026-11-27)
-- Safe to run on an existing project (idempotent). Already appended to schema.sql
-- for fresh installs.
-- =====================================================================

-- 1) Pin search_path on SECURITY DEFINER helpers (prevents search_path hijacking)
create or replace function public.is_admin()
returns boolean language sql stable security definer set search_path = public as $$
  select exists (
    select 1 from public.profiles where id = auth.uid() and role = 'admin'
  );
$$;

create or replace function public.is_assigned_coach(target_athlete uuid)
returns boolean language sql stable security definer set search_path = public as $$
  select exists (
    select 1 from public.coach_athlete_assignments
    where coach_id = auth.uid() and athlete_id = target_athlete
  );
$$;

-- 2) CRITICAL: stop users from making themselves admin.
--    Before this fix, profiles_insert_self / profiles_update_self_or_admin let any
--    signed-in user write role = 'admin' on their own row.
--    Requests with no JWT role (SQL editor / service role) are still allowed, which is
--    how you bootstrap the first admin:  update public.profiles set role='admin' where email='you@x.com';
create or replace function public.profiles_guard_role()
returns trigger language plpgsql security definer set search_path = public as $$
begin
  if auth.uid() is null and coalesce(auth.role(), '') <> 'anon' then
    return new;
  end if;
  if tg_op = 'INSERT' then
    if new.role = 'admin' and not public.is_admin() then
      raise exception 'Only administrators can create admin accounts' using errcode = '42501';
    end if;
  elsif tg_op = 'UPDATE' then
    if new.role is distinct from old.role and not public.is_admin() then
      raise exception 'Only administrators can change roles' using errcode = '42501';
    end if;
  end if;
  return new;
end;
$$;

drop trigger if exists profiles_guard_role_trg on public.profiles;
create trigger profiles_guard_role_trg
  before insert or update on public.profiles
  for each row execute function public.profiles_guard_role();

-- 3) Devices: allow an athlete to CLAIM an unowned belt (the old single "for all" policy
--    made the app's QR-pairing upsert fail with an RLS error for admin-registered belts).
drop policy if exists "devices_write" on public.devices;
drop policy if exists "devices_insert" on public.devices;
drop policy if exists "devices_update" on public.devices;
drop policy if exists "devices_delete" on public.devices;

create policy "devices_insert" on public.devices
  for insert with check (owner_athlete_id = auth.uid() or public.is_admin());
create policy "devices_update" on public.devices
  for update
  using (owner_athlete_id = auth.uid() or owner_athlete_id is null or public.is_admin())
  with check (owner_athlete_id = auth.uid() or public.is_admin());
create policy "devices_delete" on public.devices
  for delete using (owner_athlete_id = auth.uid() or public.is_admin());

-- 4) updated_at maintenance
create or replace function public.touch_updated_at()
returns trigger language plpgsql as $$
begin
  new.updated_at = now();
  return new;
end;
$$;

drop trigger if exists profiles_touch_updated_at on public.profiles;
create trigger profiles_touch_updated_at before update on public.profiles
  for each row execute function public.touch_updated_at();

drop trigger if exists races_touch_updated_at on public.races;
create trigger races_touch_updated_at before update on public.races
  for each row execute function public.touch_updated_at();

-- 5) Indexes for the RLS helper lookups and the app's common filters
create index if not exists idx_assignments_coach   on public.coach_athlete_assignments (coach_id);
create index if not exists idx_assignments_athlete on public.coach_athlete_assignments (athlete_id);
create index if not exists idx_devices_owner       on public.devices (owner_athlete_id);
create index if not exists idx_races_athlete       on public.races (athlete_id, created_at desc);
create index if not exists idx_gps_race_time       on public.gps_trackpoints (race_id, recorded_at);
create index if not exists idx_gps_athlete         on public.gps_trackpoints (athlete_id);
create index if not exists idx_obstacles_athlete   on public.obstacle_events (athlete_id, recorded_at desc);
create index if not exists idx_health_athlete_time on public.health_records (athlete_id, recorded_at desc);
create index if not exists idx_alerts_athlete      on public.alerts (athlete_id, created_at desc);
