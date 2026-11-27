-- BlindRun Kenya — Supabase PostgreSQL Schema & Row-Level Security (RLS)
-- Run this script in your Supabase Project SQL Editor.

create extension if not exists "pgcrypto";

-- 1. User Profiles (Athletes, Coaches, Administrators)
create table if not exists public.profiles (
  id uuid primary key references auth.users(id) on delete cascade,
  email text not null,
  full_name text not null,
  role text not null check (role in ('athlete', 'coach', 'admin')) default 'athlete',
  accessibility_mode text not null check (accessibility_mode in ('visually_impaired', 'deafblind', 'guide_assisted')) default 'visually_impaired',
  phone text default '',
  emergency_contact_name text default '',
  emergency_contact_phone text default '',
  haptic_intensity int not null default 85 check (haptic_intensity between 20 and 100),
  audio_cues_enabled boolean not null default true,
  consented_coach_sharing boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

-- 2. Coach-Athlete Assignments (FR-11, FR-12)
create table if not exists public.coach_athlete_assignments (
  id uuid primary key default gen_random_uuid(),
  coach_id uuid not null references public.profiles(id) on delete cascade,
  athlete_id uuid not null references public.profiles(id) on delete cascade,
  assigned_by uuid references public.profiles(id),
  created_at timestamptz not null default now(),
  unique(coach_id, athlete_id)
);

-- 3. Wearable Devices (ESP32 Smart Waist Belt Registry — FR-03, FR-04)
create table if not exists public.devices (
  id uuid primary key default gen_random_uuid(),
  serial_code text unique not null,
  owner_athlete_id uuid references public.profiles(id) on delete set null,
  name text not null,
  mac_address text not null,
  firmware_version text not null default '1.0.0',
  battery_percent int not null default 100,
  status text not null default 'registered',
  diagnostics_json jsonb default '{}'::jsonb,
  last_seen_at timestamptz default now(),
  created_at timestamptz not null default now()
);

-- 4. Races & Configurations (FR-06, FR-07)
create table if not exists public.races (
  id text primary key,
  athlete_id uuid not null references public.profiles(id) on delete cascade,
  created_by uuid references public.profiles(id),
  name text not null,
  race_type text not null default 'Long distance',
  total_distance_meters double precision not null,
  covered_distance_meters double precision not null default 0,
  remaining_distance_meters double precision not null,
  start_location_name text not null default '',
  finish_location_name text not null default '',
  route_waypoints_json jsonb default '[]'::jsonb,
  status text not null check (status in ('configured', 'running', 'paused', 'completed')) default 'configured',
  avg_pace_sec_per_km double precision not null default 0,
  avg_speed_kmh double precision not null default 0,
  elapsed_seconds bigint not null default 0,
  avg_heart_rate int not null default 0,
  max_heart_rate int not null default 0,
  active_calories int not null default 0,
  started_at timestamptz,
  finished_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

-- 5. GPS Trackpoints (FR-07, FR-10)
create table if not exists public.gps_trackpoints (
  id text primary key,
  race_id text not null references public.races(id) on delete cascade,
  athlete_id uuid not null references public.profiles(id) on delete cascade,
  latitude double precision not null,
  longitude double precision not null,
  accuracy_meters real not null default 0,
  speed_mps real not null default 0,
  distance_from_start_meters double precision not null default 0,
  deviation_meters double precision not null default 0,
  recorded_at timestamptz not null default now()
);

-- 6. Obstacle Events (FR-08, FR-10)
create table if not exists public.obstacle_events (
  id text primary key,
  race_id text references public.races(id) on delete set null,
  athlete_id uuid not null references public.profiles(id) on delete cascade,
  device_serial text not null default '',
  direction text not null check (direction in ('front', 'back', 'left', 'right')),
  severity text not null default 'medium',
  distance_cm int not null default 120,
  recorded_at timestamptz not null default now()
);

-- 7. Health Records (FR-05, FR-09, FR-10)
create table if not exists public.health_records (
  id text primary key,
  athlete_id uuid not null references public.profiles(id) on delete cascade,
  race_id text references public.races(id) on delete set null,
  heart_rate_bpm int not null default 0,
  avg_heart_rate_bpm int not null default 0,
  max_heart_rate_bpm int not null default 0,
  steps bigint not null default 0,
  active_calories int not null default 0,
  exercise_duration_sec bigint not null default 0,
  distance_meters double precision not null default 0,
  spo2_percent int,
  sleep_minutes int,
  source text not null default 'Health Connect',
  recorded_at timestamptz not null default now()
);

-- 8. Alerts & Emergency Notifications (FR-13)
create table if not exists public.alerts (
  id text primary key,
  athlete_id uuid not null references public.profiles(id) on delete cascade,
  alert_type text not null,
  title text not null,
  message text not null,
  severity text not null default 'info',
  acknowledged boolean not null default false,
  created_at timestamptz not null default now()
);

-- 9. Admin Audit Logs (FR-12)
create table if not exists public.admin_audit_logs (
  id uuid primary key default gen_random_uuid(),
  admin_id uuid not null references public.profiles(id) on delete cascade,
  action text not null,
  target_entity text not null,
  details text not null default '',
  created_at timestamptz not null default now()
);

-- Enable Row-Level Security
alter table public.profiles enable row level security;
alter table public.coach_athlete_assignments enable row level security;
alter table public.devices enable row level security;
alter table public.races enable row level security;
alter table public.gps_trackpoints enable row level security;
alter table public.obstacle_events enable row level security;
alter table public.health_records enable row level security;
alter table public.alerts enable row level security;
alter table public.admin_audit_logs enable row level security;

-- Helper functions for role-based RLS
create or replace function public.is_admin()
returns boolean language sql stable security definer as $$
  select exists (
    select 1 from public.profiles where id = auth.uid() and role = 'admin'
  );
$$;

create or replace function public.is_assigned_coach(target_athlete uuid)
returns boolean language sql stable security definer as $$
  select exists (
    select 1 from public.coach_athlete_assignments
    where coach_id = auth.uid() and athlete_id = target_athlete
  );
$$;

-- RLS Policies
create policy "profiles_self_or_coach_or_admin" on public.profiles
  for select using (
    id = auth.uid() or public.is_admin() or public.is_assigned_coach(id)
  );
create policy "profiles_insert_self" on public.profiles
  for insert with check (id = auth.uid());
create policy "profiles_update_self_or_admin" on public.profiles
  for update using (id = auth.uid() or public.is_admin());

create policy "assignments_read" on public.coach_athlete_assignments
  for select using (
    coach_id = auth.uid() or athlete_id = auth.uid() or public.is_admin()
  );
create policy "assignments_admin_write" on public.coach_athlete_assignments
  for all using (public.is_admin());

create policy "devices_read" on public.devices
  for select using (
    owner_athlete_id = auth.uid() or owner_athlete_id is null or public.is_admin() or public.is_assigned_coach(owner_athlete_id)
  );
create policy "devices_write" on public.devices
  for all using (owner_athlete_id = auth.uid() or public.is_admin());

create policy "races_read" on public.races
  for select using (
    athlete_id = auth.uid() or public.is_admin() or public.is_assigned_coach(athlete_id)
  );
create policy "races_write" on public.races
  for all using (
    athlete_id = auth.uid() or public.is_admin() or public.is_assigned_coach(athlete_id)
  );

create policy "gps_read" on public.gps_trackpoints
  for select using (
    athlete_id = auth.uid() or public.is_admin() or public.is_assigned_coach(athlete_id)
  );
create policy "gps_insert_own" on public.gps_trackpoints
  for insert with check (athlete_id = auth.uid() or public.is_admin());

create policy "obstacles_read" on public.obstacle_events
  for select using (
    athlete_id = auth.uid() or public.is_admin() or public.is_assigned_coach(athlete_id)
  );
create policy "obstacles_insert_own" on public.obstacle_events
  for insert with check (athlete_id = auth.uid() or public.is_admin());

create policy "health_read" on public.health_records
  for select using (
    athlete_id = auth.uid() or public.is_admin() or (
      public.is_assigned_coach(athlete_id) and exists (
        select 1 from public.profiles p where p.id = athlete_id and p.consented_coach_sharing = true
      )
    )
  );
create policy "health_insert_own" on public.health_records
  for insert with check (athlete_id = auth.uid() or public.is_admin());

create policy "alerts_read" on public.alerts
  for select using (
    athlete_id = auth.uid() or public.is_admin() or public.is_assigned_coach(athlete_id)
  );
create policy "alerts_write" on public.alerts
  for all using (
    athlete_id = auth.uid() or public.is_admin() or public.is_assigned_coach(athlete_id)
  );

create policy "audit_admin_only" on public.admin_audit_logs
  for all using (public.is_admin());

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
