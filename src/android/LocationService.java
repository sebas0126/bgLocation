package cordova.plugin.bgLocation;

import android.app.Service;
import android.app.Activity;

import android.os.IBinder;
import android.os.Handler;
import android.os.Bundle;
import android.os.AsyncTask;
import android.os.Environment;
import android.os.BatteryManager;

import java.io.DataOutputStream;
import java.io.FileWriter;
import java.io.File;
import java.io.BufferedWriter;
import java.io.IOException;

import java.net.HttpURLConnection;
import java.net.URL;

import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;

import android.content.SharedPreferences;
import android.content.Intent;
import android.content.IntentFilter;

import org.json.JSONObject;
import org.json.JSONException;

import android.util.Log;
import java.util.Calendar;
import java.util.Date;

import java.text.SimpleDateFormat;

public class LocationService extends Service {

  public static BufferedWriter out;

  private String logDate;

  public static String TOKEN_KEY = "token";
  public static String SERVICE_URL_KEY = "url";
  public static String USER_KEY = "idVendedor";
  private static String COORDINATES_KEY = "coordenadas";
  public static String DEVICE_KEY = "idDispositivo";
  private static String BATTERY_KEY = "bateria";
  private static String DATE_KEY = "fecha_recorrido";
  public static String SCHEDULE_KEY = "horario";
  public static String DISTANCE_KEY = "distancia";
  public static String TIME_KEY = "tiempo";

  private String PREFERENCES_NAME = "pref_location";

  private LocationManager locationManager;

  // Datos recibidos de cordova
  private String user;
  private String token;
  private String device;
  private String serviceUrl;
  // Ultima longitud y latitud registrada
  private String coordinates;
  private JSONObject horario;
  private int distance;
  private int time;

  @Override
  public void onCreate() {
    super.onCreate();
    Log.w("bgLocation", "SERVICE STARTED");

    final LocationListener mLocationListener = new LocationListener() {
      // Se llama cuando se le notifica que la posición ha cambiado
      @Override
      public void onLocationChanged(final Location location) {
        // Se conservan longitud y latitud
        coordinates = location.getLatitude() + ", " + location.getLongitude();
      }

      @Override
      public void onProviderDisabled(String provider) {
      }

      @Override
      public void onProviderEnabled(String provider) {
      }

      @Override
      public void onStatusChanged(String provider, int status, Bundle extras) {
      }
    };

    /* INTERVALO DE LLAMADOS */
    final Handler handler = new Handler();
    // Se define el codigo que se ejecutara en cada intervalo
    final Runnable runnableCode = new Runnable() {
      // Codigo ejecutado en cada intervalo
      @Override
      public void run() {
        if (workingTime()) {
          Log.w("bgLocation", "WORKING TIME");
          handler.postDelayed(this, time);

          if (coordinates != null && coordinates != "") {
            sendData();
          } else {
            Log.w("bgLocation", "NO POSITION");
            try {
              writeLog(true, "NO POSITION / " + Calendar.getInstance().getTime().toString());
            } catch (Exception e) {
            }
          }
        } else {
          // Si esta fuera de horario laboral se vuelve a verificar en una hora
          Log.w("bgLocation", "NO WORKING TIME");
          try {
            writeLog(true,
                "NO WORKING TIME / " + Calendar.getInstance().getTime().toString() + " / " + getBatteryLevel());
          } catch (Exception e) {
          }
          handler.postDelayed(this, 600000);
        }
      }

    };
    // Inicia la tarea runnable posteandola mediante el handler
    handler.post(runnableCode);

    // Obtiene el servicio de posicionamiento actual
    locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
    // Realiza peticiones de la posición de acuerdo a los parametros enviados y notifica al listener que creamos
    locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, time, distance, mLocationListener);
  }

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    Log.w("bgLocation", "SERVICE RUNNING");

    Date todayDate = Calendar.getInstance().getTime();
    SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
    logDate = formatter.format(todayDate);

    try {
      writeLog(true, "APP STATE CHANGED");
    } catch (Exception e) {
      Log.e("LOG", "FILE CREATION FAILED");
    }

    SharedPreferences settings = getApplicationContext().getSharedPreferences(PREFERENCES_NAME, Activity.MODE_PRIVATE);

    // Verifica que exista el intent y que contenga datos extras
    if (intent != null && intent.getExtras() != null) {
      // Conserva los datos recibidos
      user = intent.getExtras().getString(USER_KEY);
      token = intent.getExtras().getString(TOKEN_KEY);
      device = intent.getExtras().getString(DEVICE_KEY);
      serviceUrl = intent.getExtras().getString(SERVICE_URL_KEY);
      distance = intent.getExtras().getInt(DISTANCE_KEY);
      time = intent.getExtras().getInt(TIME_KEY);
      try {
        horario = new JSONObject(intent.getExtras().getString(SCHEDULE_KEY));
      } catch (Exception e) {
      }

      // Almaceno el usuario para recuperarlo cuando el servicio es reiniciado
      SharedPreferences.Editor editor = settings.edit();
      editor.putString(USER_KEY, user);
      editor.putString(TOKEN_KEY, token);
      editor.putString(DEVICE_KEY, device);
      editor.putString(SERVICE_URL_KEY, serviceUrl);
      editor.putString(SCHEDULE_KEY, horario.toString());
      editor.putInt(DISTANCE_KEY, distance);
      editor.putInt(TIME_KEY, time);
      editor.commit();
    } else {
      // Recupero el usuario del archivo de preferencias
      user = settings.getString(USER_KEY, "unknown");
      token = settings.getString(TOKEN_KEY, "unknown");
      device = settings.getString(DEVICE_KEY, "unknown");
      serviceUrl = settings.getString(SERVICE_URL_KEY, "unknown");
      distance = settings.getInt(DISTANCE_KEY, 15);
      time = settings.getInt(TIME_KEY, 60000);
      try {
        horario = new JSONObject(settings.getString(SCHEDULE_KEY, "{}"));
      } catch (Exception e) {
      }
    }
    // El servicio no finaliza cuando la aplicación es cerrada
    return Service.START_STICKY;
  }

  private void writeLog(Boolean append, String message) throws IOException {
    /*
     * Function to initially create the log file and it also writes the time of creation to file.
     */
    File Root = Environment.getExternalStorageDirectory();
    if (Root.canWrite()) {
      File LogFile = new File(Root, "rast-log-" + logDate + ".txt");
      FileWriter LogWriter = new FileWriter(LogFile, append);
      out = new BufferedWriter(LogWriter);
      out.write(message);
      out.write("\n");
      out.close();
    }
  }

  // Metodo para el envio de datos
  private void sendData() {
    JSONObject post_dict = new JSONObject();
    try {
      Date todayDate = Calendar.getInstance().getTime();
      SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss:SSS");
      String todayString = formatter.format(todayDate);
      logDate = todayString.substring(0, 10);

      post_dict.put("idVendedor", user);
      post_dict.put("fecha_recorrido", todayString);
      post_dict.put("coordenadas", coordinates);
      post_dict.put("idDispositivo", device);
      post_dict.put("bateria", getBatteryLevel());

      writeLog(true, post_dict.toString());
    } catch (Exception e) {
      e.printStackTrace();
    }
    new DoBackgroundTask().execute(String.valueOf(post_dict));
  }

  private String getBatteryLevel() {
    IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
    Intent batteryStatus = this.registerReceiver(null, ifilter);
    int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
    int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);

    float batteryPct = level / (float) scale * 100;

    return batteryPct + "";
  }

  private boolean workingTime() {
    String schedule = "";
    try {
      switch (Calendar.getInstance().get(Calendar.DAY_OF_WEEK)) {
      case 1:
        schedule = horario.getString("Domingo");
        Log.w("DIA SEMANA", "DOM");
        break;
      case 2:
        schedule = horario.getString("Lunes");
        Log.w("DIA SEMANA", "LUN");
        break;
      case 3:
        schedule = horario.getString("Martes");
        Log.w("DIA SEMANA", "MAR");
        break;
      case 4:
        schedule = horario.getString("Miercoles");
        Log.w("DIA SEMANA", "MIE");
        break;
      case 5:
        schedule = horario.getString("Jueves");
        Log.w("DIA SEMANA", "JUE");
        break;
      case 6:
        schedule = horario.getString("Viernes");
        Log.w("DIA SEMANA", "VIE");
        break;
      case 7:
        schedule = horario.getString("Sabado");
        Log.w("DIA SEMANA", "SAB");
        break;
      default:
        schedule = "";
      }
    } catch (Exception e) {
      Log.e("DIA SEMANA", "ERROR");
      return false;
    }
    if (schedule.equals("")) {
      return false;
    }
    String[] times = schedule.split("-");
    int startTime = Integer.parseInt(times[0].substring(0, 2));
    int endTime = Integer.parseInt(times[times.length - 1].substring(0, 2));
    int actTime = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
    return actTime < endTime && actTime >= startTime;
  }

  // Clase de tarea en background para envio de datos mediante http
  private class DoBackgroundTask extends AsyncTask<String, String, String> {

    // Tarea a ejecutar en segundo plano
    @Override
    protected String doInBackground(String... params) {

      try {

        URL url = new URL(serviceUrl);

        HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();

        urlConnection.setRequestProperty("Content-Type", "application/json");
        urlConnection.setRequestProperty("authorization", "Bearer " + token);
        urlConnection.setDoOutput(true);
        urlConnection.setDoInput(true);

        Log.w("bgLocation", params[0]);
        DataOutputStream os = new DataOutputStream(urlConnection.getOutputStream());
        os.writeBytes(params[0].toString());

        os.flush();
        os.close();

        Log.w("bgLocation", "HTTP STATUS " + String.valueOf(urlConnection.getResponseCode()));
        Log.w("bgLocation", "HTTP RESPONSE " + urlConnection.getResponseMessage());

        writeLog(true, urlConnection.getResponseCode() + "-" + urlConnection.getResponseMessage());

        urlConnection.disconnect();

      } catch (Exception e) {
        Log.e("bgLocation", "Error in GetData", e);
      }
      return "";
    }

    // Tarea a ejecutar al finalizar el doInBackground
    @Override
    protected void onPostExecute(String result) {

    }
  }

  @Override
  public IBinder onBind(Intent intent) {
    //TODO for communication return IBinder implementation
    return null;
  }
}