import React, { useState, useEffect } from 'react';
import {
  View,
  Text,
  TouchableOpacity,
  ActivityIndicator,
  Alert,
  ScrollView,
  SafeAreaView,
} from 'react-native';
import { useRouter } from 'expo-router';
import tw from 'twrnc';
import { useLocation, Coords } from '../hooks/useLocation';
import { supabase, updateLiveLocation, Route } from '../services/supabase';

export default function DriverScreen() {
  const router = useRouter();
  const { coords, errorMsg, isTracking, startTracking, stopTracking } = useLocation();
  
  // Local state
  const [routes, setRoutes] = useState<Route[]>([]);
  const [selectedRoute, setSelectedRoute] = useState<Route | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  
  // Driver Session Details (Mocked UUID for demo)
  const driverId = '8ca699a2-5813-4856-af34-453303d8dca2'; 
  const driverName = 'Julio Céspedes';

  // Stats
  const routesCompleted = 8;
  const hoursActive = '6h 30m';
  const accumulatedBob = 450;

  // Fetch routes from Supabase during mount
  useEffect(() => {
    async function loadRoutes() {
      try {
        setLoading(true);
        const { data, error } = await supabase
          .from('routes')
          .select('*')
          .order('line_code', { ascending: true });
        
        if (error) throw error;
        if (data && data.length > 0) {
          setRoutes(data);
          setSelectedRoute(data[0]); // Select first by default
        }
      } catch (err: any) {
        Alert.alert('Error', 'No se pudieron descargar las rutas de La Paz Transit: ' + err.message);
      } finally {
        setLoading(false);
      }
    }
    loadRoutes();
  }, []);

  // Sync update with Supabase whenever location updates
  const handleLocationUpdate = async (newCoords: Coords) => {
    if (!selectedRoute) return;
    try {
      const { error } = await updateLiveLocation(
        driverId,
        selectedRoute.id,
        newCoords.latitude,
        newCoords.longitude
      );
      if (error) {
        console.error('Error uploading GPS to Supabase:', error);
      }
    } catch (err) {
      console.error('Unexpected error streaming coordinates:', err);
    }
  };

  const toggleTurnType = async () => {
    if (!selectedRoute) {
      Alert.alert('Atención', 'Por favor selecciona una ruta primero.');
      return;
    }

    if (isTracking) {
      stopTracking();
      // Update driver status to inactive
      await supabase.from('drivers').update({ status: 'inactive' }).eq('id', driverId);
      Alert.alert('Turno Terminado', 'La transmisión de GPS se ha detenido correctamente.');
    } else {
      // Start real-time GPS stream
      await startTracking(handleLocationUpdate);
      // Update driver status in Supabase
      await supabase.from('drivers').update({ status: 'active' }).eq('id', driverId);
      Alert.alert(
        'Turno Activo',
        `Compartiendo ubicación en tiempo real para la ${selectedRoute.line_code} - ${selectedRoute.name}`
      );
    }
  };

  return (
    <SafeAreaView style={tw`flex-grow bg-gray-50`}>
      {/* Top Header */}
      <View style={tw`bg-[#32cd32] p-4 flex-row items-center justify-between shadow-sm`}>
        <View style={tw`flex-row items-center gap-2`}>
          <Text style={tw`text-white font-bold text-lg`}>MODO CHOFER - RUTA ACTIVA</Text>
        </View>
        <View style={tw`w-10 h-10 rounded-full bg-white/20 items-center justify-center overflow-hidden border border-white/50`}>
          <Text style={tw`text-white font-bold`}>JC</Text>
        </View>
      </View>

      <ScrollView style={tw`flex-1 p-4`} contentContainerStyle={{ paddingBottom: 40 }}>
        {/* Gamification Widget */}
        <View style={tw`bg-white rounded-2xl p-4 shadow-sm border border-gray-100 mb-4`}>
          <View style={tw`flex-row items-center justify-between mb-2`}>
            <Text style={tw`text-gray-500 font-semibold text-xs uppercase tracking-wider`}>
              Puntos Turno Actual
            </Text>
            <View style={tw`bg-lime-100 px-2 py-0.5 rounded-full`}>
              <Text style={tw`text-[#32cd32] font-bold text-xs`}>+15 pts</Text>
            </View>
          </View>
          <View style={tw`w-full bg-gray-100 rounded-full h-3 overflow-hidden`}>
            <View style={[tw`bg-[#32cd32] h-full rounded-full`, { width: '60%' }]} />
          </View>
          <Text style={tw`text-right text-gray-400 text-xs mt-1`}>9 pts para el próximo bono</Text>
        </View>

        {/* Route Selector Card */}
        <View style={tw`bg-white rounded-2xl p-4 shadow-sm border border-gray-100 mb-4`}>
          <Text style={tw`text-gray-800 font-bold mb-3`}>1. Seleccionar Ruta Predefinida</Text>
          {loading ? (
            <ActivityIndicator color="#0056b3" size="small" />
          ) : (
            <View style={tw`gap-2`}>
              {routes.map((route) => {
                const isCurrent = selectedRoute?.id === route.id;
                return (
                  <TouchableOpacity
                    key={route.id}
                    onPress={() => !isTracking && setSelectedRoute(route)}
                    disabled={isTracking}
                    style={[
                      tw`p-3 rounded-xl border flex-row items-center justify-between`,
                      isCurrent
                        ? tw`border-[#0056b3] bg-blue-50`
                        : tw`border-gray-200 bg-white`,
                      isTracking ? tw`opacity-60` : tw`opacity-100`
                    ]}
                  >
                    <View style={tw`flex-1`}>
                      <Text style={tw`text-xs font-semibold uppercase tracking-wider text-[#0056b3]`}>
                        Línea {route.line_code}
                      </Text>
                      <Text style={tw`text-gray-800 font-bold text-sm`}>
                        {route.name}
                      </Text>
                      <Text style={tw`text-gray-500 text-xs`}>
                        {route.start_point} ➔ {route.end_point}
                      </Text>
                    </View>
                    {isCurrent && (
                      <View style={tw`bg-blue-600 rounded-full w-3 h-3`} />
                    )}
                  </TouchableOpacity>
                );
              })}
            </View>
          )}
          {isTracking && (
            <Text style={tw`text-xs text-amber-600 font-medium mt-2`}>
              * Detén el turno para cambiar de ruta asignada.
            </Text>
          )}
        </View>

        {/* Live GPS Control */}
        <View style={tw`bg-white rounded-2xl p-5 shadow-sm border border-gray-100 mb-4 items-center`}>
          <Text style={tw`text-gray-800 font-bold text-base mb-3 text-center`}>
            2. Transmisión GPS en Tiempo Real
          </Text>
          <Text style={tw`text-gray-500 text-sm text-center mb-5 px-3`}>
            Al activar el turno, tu posición GPS se compartirá dinámicamente con los pasajeros suscritos.
          </Text>

          {coords && (
            <View style={tw`bg-gray-100 rounded-xl p-3 w-full mb-5 flex-row justify-around`}>
              <View style={tw`items-center`}>
                <Text style={tw`text-gray-400 text-xs font-semibold`}>Latitud</Text>
                <Text style={tw`text-gray-800 font-bold text-sm`}>
                  {coords.latitude.toFixed(6)}
                </Text>
              </View>
              <View style={tw`items-center`}>
                <Text style={tw`text-gray-400 text-xs font-semibold`}>Longitud</Text>
                <Text style={tw`text-gray-800 font-bold text-sm`}>
                  {coords.longitude.toFixed(6)}
                </Text>
              </View>
              {coords.speed !== null && (
                <View style={tw`items-center`}>
                  <Text style={tw`text-gray-400 text-xs font-semibold`}>Velocidad</Text>
                  <Text style={tw`text-gray-800 font-bold text-sm`}>
                    {Math.round((coords.speed || 0) * 3.6)} km/h
                  </Text>
                </View>
              )}
            </View>
          )}

          {errorMsg && (
            <Text style={tw`text-red-600 text-xs font-semibold mb-3 text-center`}>
              ⚠ {errorMsg}
            </Text>
          )}

          <TouchableOpacity
            onPress={toggleTurnType}
            style={[
              tw`w-full py-4 rounded-xl items-center flex-row justify-center gap-2 shadow-sm`,
              isTracking ? tw`bg-red-600` : tw`bg-[#0056b3]`
            ]}
          >
            <View style={[tw`w-3.5 h-3.5 rounded-full mr-2`, isTracking ? tw`bg-white` : tw`bg-green-400`]} />
            <Text style={tw`text-white font-bold text-base uppercase tracking-wider`}>
              {isTracking ? 'FINALIZAR TURNO / DETENER GPS' : 'INICIAR TURNO / TRANSMITIR'}
            </Text>
          </TouchableOpacity>
        </View>

        {/* Turn Performance Metrics */}
        <View style={tw`bg-white rounded-2xl p-4 shadow-sm border border-gray-100`}>
          <Text style={tw`text-gray-800 font-bold mb-3`}>Resumen de Rendimiento</Text>
          <View style={tw`flex-row gap-3 justify-between`}>
            <View style={tw`bg-gray-50 rounded-xl p-3 items-center flex-1 mx-1 border border-gray-100`}>
              <Text style={tw`text-gray-400 text-[10px] uppercase font-bold text-center`}>Cumplidas</Text>
              <Text style={tw`text-gray-900 text-lg font-bold mt-1`}>{routesCompleted}</Text>
            </View>
            <View style={tw`bg-gray-50 rounded-xl p-3 items-center flex-1 mx-1 border border-gray-100`}>
              <Text style={tw`text-gray-400 text-[10px] uppercase font-bold text-center`}>Activo</Text>
              <Text style={tw`text-gray-900 text-lg font-bold mt-1`}>{hoursActive}</Text>
            </View>
            <View style={tw`bg-green-50 rounded-xl p-3 items-center flex-1 mx-1 border border-green-100`}>
              <Text style={tw`text-[#32cd32] text-[10px] uppercase font-bold text-center`}>Acumulado</Text>
              <Text style={tw`text-green-700 text-lg font-bold mt-1`}>{accumulatedBob} BOB</Text>
            </View>
          </View>
        </View>
      </ScrollView>
    </SafeAreaView>
  );
}
