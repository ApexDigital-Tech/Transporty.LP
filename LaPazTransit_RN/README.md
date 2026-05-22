# La Paz Transit 🚍 - Configuración con Supabase & Expo

Este proyecto contiene el código base generado por un Arquitecto de Software Senior para implementar una aplicación móvil de geolocalización ligera y eficiente para la ciudad de La Paz, Bolivia, optimizada para conductores y pasajeros en tiempo real.

---

## 🛠 Requisitos Técnicos
- **Frontend:** React Native (SDK de Expo) con navegación basada en archivos usando `expo-router` y estilos con `NativeWind` (Tailwind CSS para React Native).
- **Backend:** Supabase PostgreSQL con la extensión **PostGIS** geoespacial habilitada y **Supabase Realtime (WebSockets)** para actualizaciones instantáneas.

---

## 🚀 Conexión con Supabase en 3 Pasos

### Paso 1: Crear e inicializar la Base de Datos
1. Ve a tu consola de [Supabase Dashboard](https://supabase.com/).
2. Crea un proyecto nuevo denominado `la-paz-transit`.
3. Dirígete a la pestaña del **SQL Editor** y pega íntegramente el contenido del archivo `schema.sql` provisto en este repositorio.
4. Ejecuta el SQL. Esto habilitará la extensión `postgis`, creará las tablas `routes`, `drivers`, `live_locations` con disparadores automáticos actualizando las marcas GIS, y preparará la publicación de tiempo real.

### Paso 2: Configurar las Variables de Entorno en Expo
En la raíz de tu proyecto de React Native, crea un archivo `.env` o `.env.local` con tus llaves correspondientes. El cliente de Supabase (`services/supabase.ts`) está programado para importar los siguientes tokens:

```env
EXPO_PUBLIC_SUPABASE_URL=https://tu-proyecto-id.supabase.co
EXPO_PUBLIC_SUPABASE_ANON_KEY=tu-anon-api-key-secreta-de-supabase
```

### Paso 3: Instalar Dependencias del Proyecto
Ejecuta los siguientes comandos en tu terminal para instalar las dependencias necesarias de geolocalización, almacenamiento local y dependencias principales de Supabase:

```bash
# Instalar SDK de Supabase, AsyncStorage para persistencia y Polyfill de URL
npm install @supabase/supabase-js @react-native-async-storage/async-storage react-native-url-polyfill

# Instalar librerías de Expo geolocalización y router
npx expo install expo-location expo-router expo-status-bar react-native-safe-area-context
```

---

## 📂 Estructura de Proyecto React Native
- `/app/` - Rutas de navegación con `expo-router`:
  - `DriverScreen.tsx` - Captura la posición actual del GPS de forma ultra-ligera (vigilando el uso de batería) y transmite la información al canal de live locations.
  - `PassengerScreen.tsx` - Permite al pasajero suscribirse dinámicamente y de manera reactiva en tiempo real al canal de actualizaciones de la ruta seleccionada.
- `/services/` - `supabase.ts` contiene el cliente oficial configurado para re-conexión automática y persistencia de sesión.
- `/hooks/` - `useLocation.ts` encapsula las APIs nativas de `expo-location` para gestionar permisos de GPS, geolocalización e inicialización/parada del flujo satelital.
