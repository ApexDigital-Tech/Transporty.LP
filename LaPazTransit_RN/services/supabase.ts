import 'react-native-url-polyfill/auto';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { createClient } from '@supabase/supabase-js';

// Define Supabase database types
export interface Route {
  id: string;
  line_code: string;
  name: string;
  start_point: string;
  end_point: string;
}

export interface Driver {
  id: string;
  name: string;
  status: 'active' | 'inactive' | 'offline';
}

export interface LiveLocation {
  driver_id: string;
  route_id: string;
  lat: number;
  lng: number;
  last_updated: string;
}

// Read from process.env (configured via Expo .env files / EAS Secrets)
const supabaseUrl = process.env.EXPO_PUBLIC_SUPABASE_URL || 'https://placeholder-url.supabase.co';
const supabaseAnonKey = process.env.EXPO_PUBLIC_SUPABASE_ANON_KEY || 'placeholder-anon-key';

export const supabase = createClient(supabaseUrl, supabaseAnonKey, {
  auth: {
    storage: AsyncStorage,
    autoRefreshToken: true,
    persistSession: true,
    detectSessionInUrl: false,
  },
});

/**
 * Service to update driver live coordinates
 */
export const updateLiveLocation = async (
  driverId: string,
  routeId: string,
  lat: number,
  lng: number
): Promise<{ error: any }> => {
  const { error } = await supabase
    .from('live_locations')
    .upsert(
      {
        driver_id: driverId,
        route_id: routeId,
        lat,
        lng,
      },
      { onConflict: 'driver_id' }
    );
  return { error };
};

/**
 * Service to fetch all active routes defined by Central Admin
 */
export const fetchRoutes = async (): Promise<{ data: Route[] | null; error: any }> => {
  const { data, error } = await supabase
    .from('routes')
    .select('*')
    .order('line_code', { ascending: true });
  return { data, error };
};
