package net.ekpneo.android.nmea;

import android.os.Bundle;
import android.location.Location;
import android.location.LocationManager;
import android.location.LocationListener;
import android.location.GpsStatus.NmeaListener;
import android.view.Menu;
import android.util.Log;

import java.io.IOException;
import java.io.OutputStream;
import java.io.InputStream;
import java.util.concurrent.LinkedBlockingQueue;

import ioio.lib.api.Uart;
import ioio.lib.api.exception.ConnectionLostException;
import ioio.lib.util.BaseIOIOLooper;
import ioio.lib.util.android.IOIOActivity;

public class NmeaActivity extends IOIOActivity implements NmeaListener, LocationListener {
	public static final String TAG = "NmeaActivity";
	
	private LocationManager mLocationManager;
	
	private final LinkedBlockingQueue<String> mNmeaQueue = new LinkedBlockingQueue<String>();
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_nmea);
        
        mLocationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
    }

    @Override
    public void onResume() {
    	super.onResume();
    	
    	mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, this);
    	mLocationManager.addNmeaListener(this);
    }
    
    @Override
    public void onPause() {
    	mLocationManager.removeNmeaListener(this);
    	mLocationManager.removeUpdates(this);
    	
    	super.onPause();
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_nmea, menu);
        return true;
    }
    
	@Override
	protected IOIOLooper createIOIOLooper() {
		return new IOIOLooper();
	}
	
	@Override
	public void onNmeaReceived(long timestamp, String nmea) {
		Log.i(TAG, nmea);
		
		try {
			mNmeaQueue.put(nmea);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
	
	@Override
	public void onLocationChanged(Location location) {
		// TODO Auto-generated method stub	
	}

	@Override
	public void onProviderDisabled(String provider) {
		// TODO Auto-generated method stub
	}

	@Override
	public void onProviderEnabled(String provider) {
		// TODO Auto-generated method stub
	}

	@Override
	public void onStatusChanged(String provider, int status, Bundle extras) {
		// TODO Auto-generated method stub
	}
    
	private class IOIOLooper extends BaseIOIOLooper {
		private static final int PIN_UART_RX = 6;
		private static final int PIN_UART_TX = 7;
		
		private Uart mUart;
		private InputStream mInputStream;
		private OutputStream mOutputStream;
		
		@Override
		public void setup() throws ConnectionLostException {
			try {
				mUart = ioio_.openUart(PIN_UART_RX, PIN_UART_TX, 115200, Uart.Parity.NONE, Uart.StopBits.ONE);
				mOutputStream = mUart.getOutputStream();
				mInputStream = mUart.getInputStream();
			} catch (ConnectionLostException e) {
				Log.w(TAG, "Connection to IOIO Lost");
				e.printStackTrace();
				throw e;
			}
		}
		
		@Override
		public void loop() {
			try {
				String nmea = null;
				while (mNmeaQueue.size() > 0) {
					nmea = mNmeaQueue.poll();
					Log.i(TAG, nmea);
					mOutputStream.write(nmea.getBytes()); 
				}
			} catch (IOException e) {
				Log.e(TAG, "Unable to write to serial port");
				e.printStackTrace();
			}
		}
	}
	
}
