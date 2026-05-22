import { useState, useEffect, useRef } from 'react';
import * as Location from 'expo-location';

export interface Coords {
  latitude: number;
  longitude: number;
  heading?: number | null;
  speed?: number | null;
}

export const useLocation = () => {
  const [coords, setCoords] = useState<Coords | null>(null);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [isTracking, setIsTracking] = useState<boolean>(false);
  const subscriptionRef = useRef<Location.LocationSubscription | null>(null);

  // Request permissions on mount
  useEffect(() => {
    (async () => {
      const { status } = await Location.requestForegroundPermissionsAsync();
      if (status !== 'granted') {
        setErrorMsg('Permiso de ubicación denegado.');
        return;
      }

      // Get initial position
      try {
        const initialLoc = await Location.getCurrentPositionAsync({
          accuracy: Location.Accuracy.Balanced,
        });
        setCoords({
          latitude: initialLoc.coords.latitude,
          longitude: initialLoc.coords.longitude,
          heading: initialLoc.coords.heading,
          speed: initialLoc.coords.speed,
        });
      } catch (err) {
        setErrorMsg('Error al obtener la posición inicial.');
      }
    })();

    return () => {
      stopTracking();
    };
  }, []);

  const startTracking = async (onLocationUpdate?: (newCoords: Coords) => void) => {
    if (isTracking) return;

    const { status } = await Location.getForegroundPermissionsAsync();
    if (status !== 'granted') {
      const requestRes = await Location.requestForegroundPermissionsAsync();
      if (requestRes.status !== 'granted') {
        setErrorMsg('Permiso de geolocalización no concedido.');
        return;
      }
    }

    try {
      setIsTracking(true);
      // High accuracy tracking with small distance threshold (5 meters) to reduce database spam
      subscriptionRef.current = await Location.watchPositionAsync(
        {
          accuracy: Location.Accuracy.High,
          timeInterval: 4000, // Update gps every 4 seconds
          distanceInterval: 5, // Or when moved 5 meters
        },
        (location) => {
          const newCoords: Coords = {
            latitude: location.coords.latitude,
            longitude: location.coords.longitude,
            heading: location.coords.heading,
            speed: location.coords.speed,
          };
          setCoords(newCoords);
          if (onLocationUpdate) {
            onLocationUpdate(newCoords);
          }
        }
      );
    } catch (err: any) {
      setErrorMsg(err.message || 'Error al iniciar rastreo de ubicación.');
      setIsTracking(false);
    }
  };

  const stopTracking = () => {
    if (subscriptionRef.current) {
      subscriptionRef.current.remove();
      subscriptionRef.current = null;
    }
    setIsTracking(false);
  };

  return {
    coords,
    errorMsg,
    isTracking,
    startTracking,
    stopTracking,
  };
};
