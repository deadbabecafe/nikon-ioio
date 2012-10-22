package net.ekpneo.android.nmea;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.location.Location;
import android.location.LocationManager;
import android.location.LocationListener;
import android.location.GpsStatus.NmeaListener;
import android.view.Menu;
import android.widget.TextView;
import android.widget.ToggleButton;
import android.text.format.Time;
import android.util.Log;

import java.io.IOException;
import java.io.OutputStream;
import java.io.InputStream;
import java.util.concurrent.LinkedBlockingQueue;

import ioio.lib.api.DigitalInput;
import ioio.lib.api.DigitalOutput;
import ioio.lib.api.Uart;
import ioio.lib.api.exception.ConnectionLostException;
import ioio.lib.util.BaseIOIOLooper;
import ioio.lib.util.android.IOIOActivity;

public class NmeaActivity extends IOIOActivity implements NmeaListener, LocationListener {
	public static final String TAG = "NmeaActivity";
	
	private LocationManager mLocationManager;
	
	private final LinkedBlockingQueue<String> mNmeaQueue = new LinkedBlockingQueue<String>();
	
	private ToggleButton mShutterBtn = null;
	private ToggleButton mFocusBtn = null;
	private TextView mSerialIn = null;
	
	private TextView mLatitudeTxt = null;
	private TextView mLongitudeTxt = null;
	private TextView mAltitudeTxt = null;
	private TextView mTimeTxt = null;
	private TextView mHeadingTxt = null;
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_nmea);
        
        mLocationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        
        mShutterBtn = (ToggleButton) findViewById(R.id.nmea_shutter);
        mFocusBtn = (ToggleButton) findViewById(R.id.nmea_focus);
        
        mSerialIn = (TextView) findViewById(R.id.nmea_serial);
        
        mLatitudeTxt = (TextView) findViewById(R.id.nmea_latitude);
        mLongitudeTxt = (TextView) findViewById(R.id.nmea_longitude);
        mTimeTxt = (TextView) findViewById(R.id.nmea_time);
        mHeadingTxt = (TextView) findViewById(R.id.nmea_heading);
        mAltitudeTxt = (TextView) findViewById(R.id.nmea_altitude);
    }

    @Override
    public void onResume() {
    	super.onResume();
    	
    	mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, this);
    	mLocationManager.addNmeaListener(this);
    	
    	mHandler.sendEmptyMessage(MSG_DISABLE);
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
		//Log.i(TAG, nmea);
		
		try {
			mNmeaQueue.put(nmea);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
	
	@Override
	public void onLocationChanged(Location location) {
		Time t = new Time(Time.TIMEZONE_UTC);
		t.set(location.getTime());
		mTimeTxt.setText(t.format("%m/%d/%Y %H:%M:%S"));
		mLatitudeTxt.setText(Location.convert(location.getLatitude(), Location.FORMAT_MINUTES));
		mLongitudeTxt.setText(Location.convert(location.getLongitude(), Location.FORMAT_MINUTES));
		mHeadingTxt.setText("---");
		
		if (location.hasAltitude()) {
			mAltitudeTxt.setText(Integer.toString((int) location.getAltitude()));
		} else {
			mAltitudeTxt.setText("---");
		}
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
	
	private static final int MSG_ENABLE = 1;
	private static final int MSG_DISABLE = 2;
	private static final int MSG_LOW = 3;
	private static final int MSG_HIGH = 4;
	private final Handler mHandler = new Handler() {
		public void handleMessage(Message m) {
			switch (m.what) {
			case MSG_ENABLE:
				mShutterBtn.setEnabled(true);
				mFocusBtn.setEnabled(true);
				break;
			case MSG_DISABLE:
				mShutterBtn.setEnabled(false);
				mFocusBtn.setEnabled(false);
				break;
			case MSG_LOW:
				mSerialIn.setText("LOW");
				break;
			case MSG_HIGH:
				mSerialIn.setText("HIGH");
				break;
			}
		}
	};
    
	/* A test of a few thingsㅋ
	 * - Is the DigitalInput's wait method thread-safe? (Yes)
	 * - Can we capture Nikon things fast enough? (No)
	 * 
	 * Not used currently. Bit rotting code.
	 */
	private static class WaiterThread extends Thread {
		private final Handler mHandler;
		private final DigitalInput mInput;
		
		public WaiterThread(Handler h, DigitalInput p) {
			mHandler = h;
			mInput = p;
		}
		
		public void run() {
			try {
				while (true) {
					mHandler.sendEmptyMessage(MSG_LOW);
					mInput.waitForValue(true);
					mHandler.sendEmptyMessage(MSG_HIGH);
					Thread.sleep(1000);
				}
			} catch (InterruptedException e) {
				return;
			} catch (ConnectionLostException e) {
				return;
			}
		}
	}
	
	private class IOIOLooper extends BaseIOIOLooper {
		private static final int PIN_UART_RX = 3;
		private static final int PIN_UART_TX = 12;
		private static final int PIN_SHUTTER = 11; 
		private static final int PIN_FOCUS = 10;
		private static final int PIN_TAKEN = 6;
		
		private Uart mUart;
		private DigitalOutput mShutter;
		private DigitalOutput mFocus;
		private DigitalInput mTaken;
		//private InputStream mInputStream;
		private OutputStream mOutputStream;
		
		private WaiterThread mThrd;
		
		@Override
		public void setup() throws ConnectionLostException {
			try {
				mShutter = ioio_.openDigitalOutput(PIN_SHUTTER, DigitalOutput.Spec.Mode.OPEN_DRAIN, true);
				mFocus = ioio_.openDigitalOutput(PIN_FOCUS, DigitalOutput.Spec.Mode.OPEN_DRAIN, true);
				mTaken = ioio_.openDigitalInput(PIN_TAKEN);				
				
				mUart = ioio_.openUart(new DigitalInput.Spec(PIN_UART_RX),
						new DigitalOutput.Spec(PIN_UART_TX, DigitalOutput.Spec.Mode.OPEN_DRAIN),
						4800, Uart.Parity.NONE, Uart.StopBits.ONE);
				mOutputStream = mUart.getOutputStream();
				//mInputStream = mUart.getInputStream();
				
				mHandler.sendEmptyMessage(MSG_ENABLE);
				
				//mThrd = new WaiterThread(mHandler, mTaken);
				//mThrd.run();
			} catch (ConnectionLostException e) {
				mHandler.sendEmptyMessage(MSG_DISABLE);
				Log.w(TAG, "Connection to IOIO Lost");
				throw e;
			}
		}
		
		@Override
		public void loop() throws ConnectionLostException {
			try {
				String nmea = null;
				while (mNmeaQueue.size() > 0) {
					nmea = mNmeaQueue.poll();
					//Log.i(TAG, nmea);
					mOutputStream.write(nmea.getBytes()); 
				}
				
				mShutter.write(!mShutterBtn.isChecked()); // Drive low
				mFocus.write(!mFocusBtn.isChecked());
				
				/* See if the Nikon spits out anything on TX (nope)
				int b;
				while (mInputStream.available() > 0) {
					b = mInputStream.read();
					mSerialIn.append(Integer.toHexString(b));
					mSerialIn.append(" ");
				}*/
			} catch (IOException e) {
				Log.e(TAG, "Unable to write to serial port");
				e.printStackTrace();
			} catch (ConnectionLostException e) {
				mHandler.sendEmptyMessage(MSG_DISABLE);
				throw e;
			}
		}
	}
	
}
