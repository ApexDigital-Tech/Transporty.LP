import React, { useState, useEffect, useRef } from 'react';
import {
  View,
  Text,
  TouchableOpacity,
  ActivityIndicator,
  FlatList,
  SafeAreaView,
  Alert,
} from 'react-native';
import { supabase, Route, LiveLocation } from '../services/supabase';

export default function PassengerScreen() {
  // State variables
  const [routes, setRoutes] = useState<Route[]>([]);
  const [selectedRoute, setSelectedRoute] = useState<Route | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  
  // Realtime minibus location list
  const [liveLocations, setLiveLocations] = useState<LiveLocation[]>([]);
  const subscriptionRef = useRef<any>(null);

  // Load active routes from Admin Central Database
  useEffect(() => {
    async function loadRoutesAndDefaultSelection() {
      try {
        setLoading(true);
        const { data, error } = await supabase
          .from('routes')
          .select('*')
          .order('line_code', { ascending: true });

        if (error) throw error;
        if (data && data.length > 0) {
          setRoutes(data);
          setSelectedRoute(data[0]); // select line 201 by default
        }
      } catch (err: any) {
        Alert.alert('Error', 'No se pudieron descargar las rutas de base: ' + err.message);
      } finally {
        setLoading(false);
      }
    }
    loadRoutesAndDefaultSelection();
  }, []);

  // Set up Supabase Realtime Subscription when selectedRoute changes
  useEffect(() => {
    if (!selectedRoute) return;

    // Fetch initial fleet location on the selected route
    async function getInitialLocations() {
      try {
        const { data, error } = await supabase
          .from('live_locations')
          .select('*')
          .eq('route_id', selectedRoute.id);
        
        if (error) throw error;
        if (data) {
          setLiveLocations(data);
        }
      } catch (err) {
        console.error('Error fetching initial positions:', err);
      }
    }
    
    getInitialLocations();

    // Clean up past subscriptions
    if (subscriptionRef.current) {
      supabase.removeChannel(subscriptionRef.current);
    }

    console.log(`Subscribing to realtime updates for Route Id: ${selectedRoute.id}`);

    // Create a new Supabase Realtime channel specifically monitoring live_locations
    // with a database filter on 'route_id' to save network bandwidth & battery life
    subscriptionRef.current = supabase
      .channel(`live-transit-${selectedRoute.id}`)
      .on(
        'postgres_changes',
        {
          event: '*', // Listen to INSERT, UPDATE, DELETE
          schema: 'public',
          table: 'live_locations',
          filter: `route_id=eq.${selectedRoute.id}`,
        },
        (payload) => {
          console.log('Realtime location payload received:', payload);
          const { eventType, new: newRecord, old: oldRecord } = payload;

          setLiveLocations((prevList) => {
            if (eventType === 'DELETE') {
              return prevList.filter((loc) => loc.driver_id !== oldRecord.driver_id);
            }
            
            // Check if driver is already in our local tracking state
            const exists = prevList.some((loc) => loc.driver_id === newRecord.driver_id);
            
            if (exists) {
              // Update position
              return prevList.map((loc) =>
                loc.driver_id === newRecord.driver_id ? (newRecord as LiveLocation) : loc
              );
            } else {
              // Add new driver to active track list
              return [...prevList, newRecord as LiveLocation];
            }
          });
        }
      )
      .subscribe((status) => {
        console.log(`Subscription status for ${selectedRoute.line_code}:`, status);
      });

    // Cleanup on unmount/route change
    return () => {
      if (subscriptionRef.current) {
        supabase.removeChannel(subscriptionRef.current);
      }
    };
  }, [selectedRoute]);

  return (
    <SafeAreaView className="flex-grow bg-slate-50">
      {/* Top Banner Bar */}
      <View className="bg-[#00327d] px-4 py-4 flex-row items-center justify-between shadow-md">
        <Text className="text-white font-bold text-lg tracking-tight">
          BUSCAR RUTA / PARADA
        </Text>
        <View className="bg-[#32cd32] px-3 py-1 rounded-full flex-row items-center space-x-1">
          <View className="w-2 h-2 rounded-full bg-white animate-pulse" />
          <Text className="text-white font-semibold text-xs">EN VIVO</Text>
        </View>
      </View>

      {/* Main Content Layout */}
      <View className="flex-grow p-4">
        {/* Helper Instructions Banner */}
        <View className="bg-blue-100/50 rounded-2xl p-4 border border-blue-200/50 mb-4">
          <Text className="text-blue-950 font-bold mb-1">💡 Monitoreo en Tiempo Real</Text>
          <Text className="text-blue-900 text-xs">
            Selecciona tu línea a continuación para conectarte automáticamente a la transmisión satelital de los minibuses mediante los sockets seguros de Supabase Realtime.
          </Text>
        </View>

        {/* Horizontal Route Quick Chips */}
        <Text className="text-gray-500 font-bold text-xs uppercase tracking-wider mb-2">
          Rutas Disponibles La Paz
        </Text>
        {loading ? (
          <ActivityIndicator color="#00327d" size="small" className="my-4" />
        ) : (
          <View className="flex-row flex-wrap mb-4">
            {routes.map((route) => {
              const isSelected = selectedRoute?.id === route.id;
              return (
                <TouchableOpacity
                  key={route.id}
                  onPress={() => setSelectedRoute(route)}
                  className={`mr-2 mb-2 px-4 py-2 rounded-full border ${
                    isSelected
                      ? 'bg-[#00327d] border-[#00327d]'
                      : 'bg-white border-gray-200'
                  }`}
                >
                  <Text
                    className={`font-bold text-xs ${
                      isSelected ? 'text-white' : 'text-gray-600'
                    }`}
                  >
                    Línea {route.line_code}
                  </Text>
                </TouchableOpacity>
              );
            })}
          </View>
        )}

        {/* Selected Route Info Panel */}
        {selectedRoute && (
          <View className="bg-white rounded-2xl p-4 shadow-sm border border-gray-100 mb-4">
            <View className="flex-row items-center justify-between">
              <View>
                <Text className="text-xs font-semibold uppercase text-blue-600 tracking-wider">
                  Detalles de la Selección
                </Text>
                <Text className="text-gray-800 font-extrabold text-lg mt-0.5">
                  {selectedRoute.name}
                </Text>
              </View>
              <View className="w-10 h-10 rounded-full bg-blue-100 items-center justify-center">
                <Text className="text-[#0056b3] font-bold text-xs">Bus</Text>
              </View>
            </View>
            <View className="flex-row items-center space-x-2 mt-3 pt-2 border-t border-gray-50">
              <Text className="text-xs font-bold text-gray-700">Origen:</Text>
              <Text className="text-xs text-gray-500">{selectedRoute.start_point}</Text>
              <Text className="text-xs font-bold text-gray-700 ml-2">Destino:</Text>
              <Text className="text-xs text-gray-500">{selectedRoute.end_point}</Text>
            </View>
          </View>
        )}

        {/* Subscribed Active Vehicles List */}
        <Text className="text-gray-500 font-bold text-xs uppercase tracking-wider mb-2">
          Copilotos / Minibuses en Ruta ({liveLocations.length})
        </Text>

        {liveLocations.length === 0 ? (
          <View className="bg-orange-50/50 border border-orange-200 rounded-2xl p-6 items-center justify-center">
            <Text className="text-orange-950 font-bold text-center">No hay unidades transmitiendo</Text>
            <Text className="text-orange-900 text-xs text-center mt-1">
              Actualmente no hay choferes en turno enviando GPS en esta ruta. Inicia sesión en el panel del Chofer para simular y ver actualizaciones en tiempo real instantáneas.
            </Text>
          </View>
        ) : (
          <FlatList
            data={liveLocations}
            keyExtractor={(item) => item.driver_id}
            renderItem={({ item }) => (
              <View className="bg-white rounded-xl p-4 mb-2 shadow-sm border border-gray-100 flex-row items-center justify-between">
                <View className="flex-row items-center space-x-3">
                  <View className="bg-[#32cd32]/10 p-2.5 rounded-full">
                    <Text className="text-[#32cd32] font-bold text-sm">📡</Text>
                  </View>
                  <View>
                    <Text className="text-gray-800 font-bold text-sm">
                      Minibús Activo
                    </Text>
                    <Text className="text-gray-400 text-xs">
                      Lat: {item.lat.toFixed(6)} | Lng: {item.lng.toFixed(6)}
                    </Text>
                  </View>
                </View>
                <View className="items-end">
                  <Text className="text-[#32cd32] font-bold text-sm">~4 min</Text>
                  <Text className="text-[10px] text-gray-400">Distancia: ~1.2 km</Text>
                </View>
              </View>
            )}
          />
        )}
      </View>
    </SafeAreaView>
  );
}
