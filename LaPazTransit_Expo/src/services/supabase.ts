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

const supabaseUrl = 'https://wwnxwdliysojiqtefuoy.supabase.co';
const supabaseAnonKey = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Ind3bnh3ZGxpeXNvamlxdGVmdW95Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3Nzk0Njc5NTYsImV4cCI6MjA5NTA0Mzk1Nn0.qX81MiWsByNhaer_UEEmGoFAEfmYqyJk9gj6slq_c98';

export const supabase = createClient(supabaseUrl, supabaseAnonKey, {
  auth: {
    storage: AsyncStorage,
    autoRefreshToken: true,
    persistSession: true,
    detectSessionInUrl: false,
  },
});

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

export const fetchRoutes = async (): Promise<{ data: Route[] | null; error: any }> => {
  const { data, error } = await supabase
    .from('routes')
    .select('*')
    .order('line_code', { ascending: true });
  return { data, error };
};
