package cordova.plugin.bgLocation;

import cordova.plugin.bgLocation.LocationService;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.Manifest;
import android.content.pm.PackageManager;

import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.PluginResult;
import org.apache.cordova.PermissionHelper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * This class echoes a string called from JavaScript.
 */
public class BgLocation extends CordovaPlugin {

	private final int LOCATION_PERM = 113;

	private JSONArray args;

	// Se ejecuta el metodo solicitado desde el Javascript
	@Override
	public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
		Log.w("bgLocation", "START EXCECUTION");
		if (action.equals("initLocation")) {
			this.args = args;
			if (!PermissionHelper.hasPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)) {
				PermissionHelper.requestPermission(this, LOCATION_PERM, Manifest.permission.ACCESS_FINE_LOCATION);
			} else {
				this.initLocation();
			}

			return true;
		}
		return false;
	}

	public void onRequestPermissionResult(int requestCode, String[] permissions, int[] grantResults)
			throws JSONException {
		for (int r : grantResults) {
			if (r == PackageManager.PERMISSION_DENIED) {
				Log.e("PERMISSION", "NO PERMISSION ALLOWED");
				return;
			}
		}
		switch (requestCode) {
		case LOCATION_PERM:
			Log.w("PERMISSION", "LOCATION");
			this.initLocation();
			break;
		default:
			Log.w("PERMISSION", "ANOTHER");
		}
	}

	private void initLocation() {
		Log.w("bgLocation", "SERVICE PRE-INITIALIZATION");
		try {
			// Se crea un nuevo intent para ejecutar el servicio en background
			Intent intent = new Intent(cordova.getActivity().getApplicationContext(), LocationService.class);
			// Bundle para transmitir datos al servicio
			Bundle bundle = new Bundle();
			// Se agregan los datos a transmitir

			bundle.putString(LocationService.TOKEN_KEY, args.getString(0));
			bundle.putString(LocationService.USER_KEY, args.getString(1));
			bundle.putString(LocationService.DEVICE_KEY, args.getString(2));
			bundle.putString(LocationService.SERVICE_URL_KEY, args.getString(3));
			bundle.putString(LocationService.SCHEDULE_KEY, args.getString(4));
			bundle.putInt(LocationService.DISTANCE_KEY, args.getInt(5));
			bundle.putInt(LocationService.TIME_KEY, args.getInt(6));
			intent.putExtras(bundle);

			//Se inicia el servicio
			Log.w("bgLocation", "SERVICE STARTING");
			cordova.getActivity().startService(intent);
		} catch (Exception e) {
			Log.w("bgLocation", "SERVICE ERROR" + e.getMessage());
		}
	}

}
