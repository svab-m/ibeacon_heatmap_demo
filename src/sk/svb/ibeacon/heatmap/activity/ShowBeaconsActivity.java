package sk.svb.ibeacon.heatmap.activity;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import sk.svb.ibeacon.heatmap.R;
import sk.svb.ibeacon.heatmap.db.DatabaseHelper;
import sk.svb.ibeacon.heatmap.logic.HeatPoint;
import sk.svb.ibeacon.heatmap.logic.IBeaconHeatMap;
import sk.svb.ibeacon.heatmap.logic.MyBeaconRaw;
import sk.svb.ibeacon.heatmap.logic.MyIBeaconDevice;
import sk.svb.ibeacon.heatmap.support.Logger;
import sk.svb.ibeacon.heatmap.support.MySupport;
import uk.co.alt236.bluetoothlelib.device.BluetoothLeDevice;
import uk.co.alt236.bluetoothlelib.util.IBeaconUtils;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothAdapter.LeScanCallback;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.PointF;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.Toast;

/**
 * the position of iBeacons is fixed: place your beacon as you see on screen (by
 * colors) draw beacon accuraties<br>
 * draw heatmap, based on your locatin to iBeacons<br>
 * if accuraties are close enought, a heat point is created - or heated up<br>
 * 
 * @author mbodis
 *
 */
@SuppressLint("NewApi")
@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
public class ShowBeaconsActivity extends Activity {

	private static final String TAG = "ShowBeaconsActivity";

	private static final int ALPHA = 20;
	private static final int NO_ALPHA = 255;
	private static final int RADIUS_CONST = 100;

	// layout, common
	private MySurfaceView mySurfaceView;
	float dn;
	private int method = -1;
	private boolean toggleLog = false;
	private boolean toggleIBeacons = true;
	private boolean toggleHeatMap = false;
	private boolean testTriangle = false;

	// ibeacon scan
	List<?> myBeacons;
	private BluetoothAdapter mBluetoothAdapter;
	private LeScanCallback mLeScanCallback;

	// canvas
	private boolean initCanvasSizes = false;
	private float margin; // canvas.height/20
	private float meter; // meter in pixels
	private int roomW, roomH;

	// canvas ibeacons
	private float radiusR = 0f;// 0.5f;
	private float radiusG = 0f;// 1f;
	private float radiusB = 0f;// 2f;
	float top, v, rx, ry, gx, gy, bx, by;

	// canvas colors
	private Paint pR, pG, pB, pBL, psqBL;
	private Paint pRa, pGa, pBa, pBLa;

	// heatMap
	private boolean useHeatMap = false;
	private IBeaconHeatMap hmb;
	private Bitmap gradient;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		hmb = new IBeaconHeatMap();

		dn = getResources().getDisplayMetrics().density;

		if (getIntent() != null) {
			method = getIntent().getIntExtra("method", -1);
			roomW = getIntent().getIntExtra("r_width", -1);
			roomH = getIntent().getIntExtra("r_height", -1);
			Log.d(TAG, "selected method: " + method);
		}
		if (method == -1 || roomW == -1 || roomH == -1) {
			Toast.makeText(getApplicationContext(), "No method selected ERR",
					Toast.LENGTH_SHORT).show();
			finish();
		}
		initBtLe();
		initColors();
		mySurfaceView = new MySurfaceView(this);
		setContentView(mySurfaceView);

		gradient = BitmapFactory.decodeResource(getResources(),
				R.drawable.heat_map);

		gradient = MySupport.getResizedBitmap(gradient, (int) (20 * dn),
				(int) (180 * dn));

	}

	@Override
	protected void onPause() {
		mBluetoothAdapter.stopLeScan(mLeScanCallback);
		super.onPause();
		mySurfaceView.onPause();
	}

	@Override
	protected void onResume() {
		mBluetoothAdapter.startLeScan(mLeScanCallback);
		super.onResume();
		mySurfaceView.onResume();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.ibeacon_menu, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		int id = item.getItemId();
		if (id == R.id.action_toggle_log) {
			toggleLog = !toggleLog;
			Toast.makeText(
					getApplicationContext(),
					getString(R.string.toast_debug_state)
							+ (toggleLog ? getString(R.string.on)
									: getString(R.string.off)),
					Toast.LENGTH_SHORT).show();
			return true;
		} else if (id == R.id.action_toggle_heatmap) {
			toggleHeatMap = !toggleHeatMap;
			Toast.makeText(
					getApplicationContext(),
					getString(R.string.toast_heatmap_state)
							+ (toggleHeatMap ? getString(R.string.on)
									: getString(R.string.off)),
					Toast.LENGTH_SHORT).show();
			return true;
		} else if (id == R.id.action_toggle_ibeacons) {
			toggleIBeacons = !toggleIBeacons;
			Toast.makeText(
					getApplicationContext(),
					getString(R.string.toast_ibeacons_state)
							+ (toggleIBeacons ? getString(R.string.on)
									: getString(R.string.off)),
					Toast.LENGTH_SHORT).show();
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	/**
	 * btLe scan runs all the time activity runs
	 */
	private void initBtLe() {
		final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
		mBluetoothAdapter = bluetoothManager.getAdapter();

		myBeacons = DatabaseHelper.getSavedBeacons(getApplicationContext(),
				method);

		useHeatMap = (myBeacons.size() == 3);

		mLeScanCallback = new LeScanCallback() {

			@Override
			public void onLeScan(final BluetoothDevice device, final int rssi,
					final byte[] scanRecord) {

				runOnUiThread(new Runnable() {
					@Override
					public void run() {

						final BluetoothLeDevice deviceLe = new BluetoothLeDevice(
								device, rssi, scanRecord,
								System.currentTimeMillis());

						if (IBeaconUtils.isThisAnIBeacon(deviceLe)) {
							MyIBeaconDevice ibeacon = new MyIBeaconDevice(
									deviceLe);

							for (int i = 0; i < myBeacons.size(); i++) {

								// match beacon
								if (((MyBeaconRaw) myBeacons.get(i))
										.getDeviceAddress().equals(
												ibeacon.getAddress())) {

									((MyBeaconRaw) myBeacons.get(i))
											.setAccuracy(ibeacon.getAccuracy(),
													System.currentTimeMillis());

									switch (((MyBeaconRaw) myBeacons
											.get(i)).getNumber()) {
									case 1:
										radiusR = (float) ((MyBeaconRaw) myBeacons
												.get(i)).getAccuracy();
										// log_beacon(ibeacon.getAccuracy(),
										// "acuracy" + method + ".txt");

										break;
									case 2:
										radiusG = (float) ((MyBeaconRaw) myBeacons
												.get(i)).getAccuracy();
										break;
									case 3:
										radiusB = (float) ((MyBeaconRaw) myBeacons
												.get(i)).getAccuracy();
										break;

									}
								}
							}

						}

					}
				});

			}
		};
	}

	/**
	 * logging beacon accuraties to file, for better visualization
	 */
	private void log_beacon(double acc, String filename) {
		String secNow = new SimpleDateFormat("ss", Locale.US).format(new Date(
				System.currentTimeMillis()));
		Logger.addLog(getApplicationContext(), filename, "," + secNow + ","
				+ acc);
	}

	private void initColors() {
		pR = new Paint();
		pR.setColor(Color.RED);
		pR.setTextSize(10 * dn);
		pG = new Paint();
		pG.setColor(Color.GREEN);
		pG.setTextSize(10 * dn);
		pB = new Paint();
		pB.setColor(Color.BLUE);
		pB.setTextSize(10 * dn);
		pBL = new Paint();
		pBL.setColor(Color.BLACK);
		pBL.setTextSize(10 * dn);

		psqBL = new Paint();
		psqBL.setColor(Color.BLACK);
		psqBL.setStyle(Style.STROKE);

		pRa = new Paint();
		pRa.setColor(Color.RED);
		pRa.setAlpha(ALPHA);
		pGa = new Paint();
		pGa.setColor(Color.GREEN);
		pGa.setAlpha(ALPHA);
		pBa = new Paint();
		pBa.setColor(Color.BLUE);
		pBa.setAlpha(ALPHA);
		pBLa = new Paint();
		pBLa.setColor(Color.BLACK);
		pBLa.setAlpha(ALPHA);
	}

	public class MySurfaceView extends SurfaceView implements Runnable {

		private Thread t = null;
		private volatile boolean isAlive = false; // forbidden to save in cache
		private SurfaceHolder holder = null;

		public MySurfaceView(Context context) {
			super(context);

			holder = getHolder();

		}

		@Override
		public void run() {

			while (!t.isInterrupted()) {

				if (!holder.getSurface().isValid()) {
					continue;
				}

				Canvas c = holder.lockCanvas();
				myDraw(c);
				holder.unlockCanvasAndPost(c);

			}
		}

		public void onPause() {

			try {
				t.interrupt();
				t.join();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}

			t = null;
		}

		public void onResume() {

			t = new Thread(this);
			t.start();
		}

		/**
		 * draw beacon accuraties<br>
		 * draw heatmap, based on your locatin to iBeacons<br>
		 * if accuraties are close enought, add a heat point
		 */
		protected void myDraw(Canvas canvas) {

			if (!initCanvasSizes) {
				initCanvas(canvas);
			}

			canvas.drawARGB(255, 255, 255, 255);

			canvas.drawRect(margin, margin, margin + roomW * meter, margin
					+ roomH * meter, psqBL);
			canvas.drawCircle(rx, ry, 4, pR);
			canvas.drawCircle(gx, gy, 4, pG);
			canvas.drawCircle(bx, by, 4, pB);

			// show accuraties from iBeacons
			if (toggleIBeacons) {
				canvas.drawCircle(rx, ry, radiusR * meter, pRa);
				canvas.drawCircle(gx, gy, radiusG * meter, pGa);
				canvas.drawCircle(bx, by, radiusB * meter, pBa);
			}

			// if you have set 3 beacons we can use heatMap
			if (useHeatMap && toggleHeatMap) {
				hmb.addBeaconAccuracy(System.currentTimeMillis(), canvas,
						meter, new PointF(rx, ry), new PointF(gx, (int) gy),
						new PointF(bx, by), radiusR * meter, radiusG * meter,
						radiusB * meter);

				// drawing heat map
				for (HeatPoint hp : hmb.getHeatPointList()) {
					Paint p = new Paint();
					p.setARGB(
							NO_ALPHA,
							255 / HeatPoint.MAX_HEAT * hp.getHeatLevel(),
							0,
							255 / HeatPoint.MAX_HEAT * HeatPoint.MAX_HEAT
									- hp.getHeatLevel());
					canvas.drawPoint(hp.x, hp.y, p);
				}
			}

			// hidden demo
			if (testTriangle) {
				testDemo(canvas);
			}

			// draw logs, heatmap at bottom, circle penetration
			if (toggleLog) {
				drawLogs(canvas);
				drawCircleIntersection(canvas);
			}

		}
	}

	/**
	 * debug mode, draw intersection of circles
	 */
	private void drawCircleIntersection(Canvas canvas) {
		// test intersection of red and green
		// List<PointF> te = IBeaconHeatMap.intersectionOfTwoCircles(new
		// PointF(rx,
		// ry), radiusR * meter, new PointF(gx, gy), radiusG * meter);
		// for (PointF point : te) {
		// canvas.drawCircle(point.x, point.y, 4, pBL);
		// }

		// testing intersection all 3 circles
		List<PointF> penetrList = IBeaconHeatMap
				.getPenetrationOfThreeCircles(new PointF(rx, ry), radiusR
						* meter, new PointF(gx, gy), radiusG * meter,
						new PointF(bx, by), radiusB * meter);
		for (PointF point : penetrList) {

			canvas.drawCircle(point.x, point.y, 4, pBL);
		}
	}

	/**
	 * initialize canvas variables
	 */
	private void initCanvas(Canvas canvas) {
		initCanvasSizes = true;

		margin = canvas.getHeight() / 20;

		// use canvas.width as main parameter
		if ((canvas.getHeight() - 2 * margin) / roomH * roomW > canvas
				.getWidth()) {
			meter = (canvas.getWidth() - 2 * margin) / roomW;

			// use canva.height as main parameter
		} else {
			meter = (canvas.getHeight() - 2 * margin) / roomH;
		}

		// red
		rx = (int) (margin + (double) roomW / 2 * meter);
		ry = margin;
		// green
		gx = margin;
		gy = (int) (margin + (double) roomH / 2 * meter);
		// blue
		bx = margin + roomW * meter;
		by = (int) (margin + (double) roomH / 2 * meter);

	}

	/**
	 * testing, 3 beacon 2 meters distance each
	 */
	private void testDemo(Canvas canvas) {
		v = (float) Math.sqrt(200 * 200 - 100 * 100);
		top = canvas.getHeight() / 2 - 100;

		// red
		rx = canvas.getWidth() / 2;
		ry = top;
		// green
		gx = canvas.getWidth() / 2 - 100;
		gy = top + v;
		// blue
		bx = canvas.getWidth() / 2 + 100;
		by = top + v;

		canvas.drawARGB(255, 255, 255, 255);
		canvas.drawCircle(rx, ry, 4, pR);
		canvas.drawCircle(gx, gy, 4, pG);
		canvas.drawCircle(bx, by, 4, pB);

		canvas.drawCircle(rx, ry, radiusR * RADIUS_CONST, pRa);
		canvas.drawCircle(gx, gy, radiusG * RADIUS_CONST, pGa);
		canvas.drawCircle(bx, by, radiusB * RADIUS_CONST, pBa);
	}

	/**
	 * draw logs:<br>
	 * - radius in meters of red, green, blue iBeacon<br>
	 * - pixel representation 1 meter<br>
	 * - heatmap scale<br>
	 */
	private void drawLogs(Canvas canvas) {
		canvas.drawText("red: " + radiusR + " m", 10 * dn, 20 * dn, pR);
		canvas.drawText("green: " + radiusG + " m", 10 * dn, 30 * dn, pG);
		canvas.drawText("blue: " + radiusB + " m", 10 * dn, 40 * dn, pB);
		canvas.drawText("1 meter", 10 * dn, 65 * dn, pBL);
		canvas.drawLine(10, 70 * dn, meter + 10, 70 * dn, pBL);
		canvas.drawLine(10, 65 * dn, 10, 75 * dn, pBL);
		canvas.drawLine(meter + 10, 65 * dn, meter + 10, 75 * dn, pBL);
		canvas.drawBitmap(gradient, 10,
				canvas.getHeight() - gradient.getHeight() - 10, null);
	}

}