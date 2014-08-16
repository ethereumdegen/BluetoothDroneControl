package ru.driim.bluetoothterminal;

/*
 * Originall by Driim
 * 
 * Modified by Andrew Mazzola to fix bugs
 * 
 * 
 */
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;


import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.ParcelUuid;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity 
implements android.view.View.OnClickListener,
OnItemSelectedListener
{
	@Override
	protected void onDestroy() {
		super.onDestroy();

		unregisterReceiver(mReceiver);
	}

	private static final int REQUEST_ENABLE_BT = 105;
	public static final int MESSAGE_READ = 106;
	public static final String uuidStr= "00001101-0000-1000-8000-00805F9B34FB";
	private static ArrayAdapter<String> devicesListAdapter;
	private ArrayList<BluetoothDevice>  devicesList;
	private TextView terminal;
	private EditText sendMessage;
	private BluetoothDevice currentDevice;
	private BluetoothAdapter mBluetoothAdapter;
	private BluetoothConnection bConnection;
	public static UUID uuid = UUID.fromString(uuidStr);
	private final Handler mHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) 
		{  // read message


			switch(msg.what) 
			{
			case MESSAGE_READ: 
				// read data from bluetooth
				toastMessage(R.string.SocketRead, Toast.LENGTH_SHORT);

				byte[] buffer = new byte[msg.arg1];
				buffer = (byte[])msg.obj;

				CharSequence message = new String(buffer);
				smprintMessageOnTerminal(message);
				break;

			default :
				break;
			}
		}
	};

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		terminal = (TextView)findViewById(R.id.TerminalView);
		sendMessage = (EditText)findViewById(R.id.sendMessage);
		// Current paired by bluetooth device now
		currentDevice = null;
		bConnection = null;
		
		DroneController droneController = new DroneController();
		droneController.start();
		
		devicesList = new ArrayList<BluetoothDevice>();
		//prepare adapter for the spinner
		devicesListAdapter = new ArrayAdapter<String>(getApplicationContext(), android.R.layout.simple_spinner_item);
		devicesListAdapter.setDropDownViewResource(R.layout.spinner_layout);
		devicesListAdapter.add(getString(R.string.defaultSpinnerMessage));
		// init spinner
		Spinner spinner = (Spinner) findViewById(R.id.bluetoothDevice);
		spinner.setOnItemSelectedListener(this);
		spinner.setAdapter(devicesListAdapter);

		// Register the BroadcastReceiver
		IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
		registerReceiver(mReceiver, filter); 

		Button sendButton = (Button) findViewById(R.id.sendButton);
		sendButton.setOnClickListener(this);
		// bluetooth routines, init and find devices
		mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

		if (null == mBluetoothAdapter) {
			//Bluetooth isn't supported
			toastMessage(R.string.UnsupportedBluetooth, Toast.LENGTH_SHORT);

			// wait to show message, and exit		
			try {
				Thread.sleep(500);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}

			finish();
		} else {

			if (!mBluetoothAdapter.isEnabled()) {
				//switching on bluetooth
				Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
				startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
			} else {
				// if bluetooth switched on search devices
				if(!getPairedDevices(devicesListAdapter)) {

					if(!mBluetoothAdapter.startDiscovery()) {
						toastMessage(R.string.searchFailed, Toast.LENGTH_SHORT);
					} else {
						devicesListAdapter.clear();
					}
				}
			}
		}
	}

	class DroneController extends Thread {
		@Override
		public void run()
		{
			while(true)
			{
				try
				{
				
				CharSequence message = "H";
				printMessageOnTerminal(message);
				byte[] msg = charSequenceToByteArray(message);
				bConnection.write(msg);
				}catch(Exception e)
				{
				e.printStackTrace();
				}
				
				try {
					Thread.sleep(5000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}
		
		
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.activity_main, menu);
		return true;
	}

	private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
		@Override public void onReceive(Context context, Intent intent) {
			CharSequence message = "Finded device...";
			printMessageOnTerminal(message);

			String action = intent.getAction();
			// When discovery finds a device
			if (BluetoothDevice.ACTION_FOUND.equals(action)) {
				// Get the BluetoothDevice object from the Intent
				BluetoothDevice device = (BluetoothDevice)intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
				//ParcelUuid [] pui = (ParcelUuid [])intent.getParcelableExtra(BluetoothDevice.EXTRA_UUID)
				// Add the name and address to an array adapter to show in a ListView
				String name = device.getName();
				if(!name.isEmpty() && name.length() < 8)
				{
					devicesListAdapter.add( name );
					devicesList.add(device);
				}
				else
				{
					printMessageOnTerminal("device name is empty");
				}
				
			}
		}
	};

	@Override
	public void onClick(View v) {
		switch(v.getId())
		{
		case R.id.sendButton:
			CharSequence message = sendMessage.getText();
			if (null != currentDevice && null != bConnection) {
				printMessageOnTerminal(message);
				byte[] msg = charSequenceToByteArray(message);
				bConnection.write(msg);
			} else {
				toastMessage(R.string.noCurrentDevice, Toast.LENGTH_SHORT);
			}
			break;

		default:
			toastMessage(R.string.messageUnknownButton, Toast.LENGTH_SHORT);
			break;
		}
	}

	public void printMessageOnTerminal(CharSequence message) {
		final CharSequence seq = message;
		runOnUiThread(new Runnable() {
		     @Override
		     public void run() {

		//stuff that updates ui
		    	 terminal.append("\n#>");
		    	 terminal.append(seq);
		    }
		});
		
	}
	
	public void smprintMessageOnTerminal(CharSequence message) {
		/*terminal.append("\n#>");
		for(int i=0; i< message.length(); i++)
		{
			int m = (int)message.charAt(i);
			terminal.append(String.valueOf(m));
			terminal.append(" ");
		}*/
		
		final CharSequence seq = message;
		runOnUiThread(new Runnable() {
		     @Override
		     public void run() {
		    		for(int i=0; i< seq.length(); i++)
		    		{
		    			int m = (int)seq.charAt(i);
		    			terminal.append(String.valueOf(m));
		    			terminal.append(" ");
		    		}
		    }
		});
		
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		// TODO: implement this method
		return true;
	}
	
	public byte[] charSequenceToByteArray(CharSequence message)
	{
		
		int length = message.length();
		if(length == 0)
			return null;
		
		byte[] retval = new byte[length];
		
		for(int i=0; i<length; i++)
		{
			retval[i] = (byte) message.charAt(i);
		}
		
		return retval;
	}

	@Override
	protected void onActivityResult (int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);

		switch(requestCode) {
		// bluetooth
		case REQUEST_ENABLE_BT:
			if(RESULT_OK == resultCode) {
				// bluetooth is switched on now
				if(!getPairedDevices(devicesListAdapter)) {
					if(!mBluetoothAdapter.startDiscovery()) {
						toastMessage(R.string.searchFailed, Toast.LENGTH_SHORT);
					}
				}
			} else {
				// bt isn't switched on
				toastMessage(R.string.notSwithcedOnBluetooth, Toast.LENGTH_SHORT);

				try {
					Thread.sleep(500);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}

				finish();
			}
			break;

		default:
			break;
		}
	}


	private boolean getPairedDevices(ArrayAdapter<String> devices) {

		boolean retval = false;
		CharSequence message = "Searching paired diveces";
		CharSequence message2 = "Search end";

		printMessageOnTerminal(message);

		mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
		Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();

		if (pairedDevices.size() > 0) {
			retval = true;

			devices.clear();
			devices.add(getString(R.string.findedPairedDevices));

			for(BluetoothDevice device : pairedDevices) {
				devices.add(device.getName());
				devicesList.add(device);
			}
		} else {
			devices.clear();
			devices.insert(getString(R.string.noPairedDevices), 0);
		}
		
		printMessageOnTerminal(message2);

		return retval;
	}

	void toastMessage(int message, int duration) {
		Toast toastMessage = Toast.makeText(getApplicationContext(), message, duration);
		toastMessage.setGravity(Gravity.CENTER, 0, 0);
		toastMessage.show();
	}

	@Override
	public void onItemSelected(AdapterView<?> parent, View view, int pos,
			long id) {
		// TODO Auto-generated method stub
		String current;
		try {
			current = (String)parent.getItemAtPosition(pos);
			String mes = "Item selected ";
			mes += pos;
			mes += current;
			printMessageOnTerminal(mes);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			//e.printStackTrace();
			String mes1 = "Exception";
			printMessageOnTerminal(mes1);
		}
//		for(BluetoothDevice device : devicesList) {
//			if(current == device.getName()) {
//				currentDevice = device;
//				bConnection = new BluetoothConnection(mBluetoothAdapter, 
//													  currentDevice.getName(), 
//													  currentDevice.getUuids()[0].getUuid(),
//													  mHandler);
//				break;
//			}
//		}
		
		try {
			currentDevice = devicesList.get(pos);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			String mes2 = "Exception 2";
			printMessageOnTerminal(mes2);
		}
		
		try {
//			ParcelUuid[] pui = currentDevice.getUuids();
//			if(pui == null)
//			{
//				String mes4 = "NULL UUIDS";
//				printMessageOnTerminal(mes4);
//			}
//			UUID ui = pui[0].getUuid();
			String name = currentDevice.getName();
//			if(ui == null)
//			{
//				String mes4 = "NULL UUID";
//				printMessageOnTerminal(mes4);
//			}
			if(name == null)
			{
				String mes5 = "NULL NAME";
				printMessageOnTerminal(mes5);
			}
			if(currentDevice == null)
			{
				String mes6 = "NULL DEVICE";
				printMessageOnTerminal(mes6);
			}
			
			if(mHandler == null)
			{
				String mes6 = "NULL HANDLER";
				printMessageOnTerminal(mes6);
			}
			bConnection = new BluetoothConnection(currentDevice, 
					  name, 
					  uuid,
					  mHandler, this);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			String mes3 = "Exception 3";
			printMessageOnTerminal(mes3 + e.getMessage());
		}
		
		try {
			bConnection.start();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			String mes3 = "Exception 4";
			printMessageOnTerminal(mes3 + e.getMessage());
		}
		

		
		
	}

	@Override
	public void onNothingSelected(AdapterView<?> arg0) {
		// TODO Auto-generated method stub

	}

}



// TODO: Do a write function
class BluetoothConnection extends Thread {
	//private final BluetoothServerSocket mmServerSocket; 
	private final BluetoothSocket mmServerSocket; 
	private final Handler mHandler;
	private InputStream inStream;
	private OutputStream outStream;
	private MainActivity act;

	//public BluetoothConnection(BluetoothAdapter adapter, String Name, UUID uuid, Handler mHand, MainActivity ac) {
	public BluetoothConnection(BluetoothDevice adapter, String Name, UUID uuid, Handler mHand, MainActivity ac) {
		// Use a temporary object that is later assigned to mmServerSocket,
		// because mmServerSocket is final
		BluetoothSocket tmp = null;
		inStream = null;
		outStream = null;
		mHandler = mHand;
		act = ac;
		try {
			// MY_UUID is the app's UUID string, also used by the client code
			//tmp = adapter.listenUsingRfcommWithServiceRecord(Name, uuid);
			tmp = adapter.createRfcommSocketToServiceRecord(uuid);
		} catch (IOException e) {
			CharSequence message2 = "error socket creation";
			act.printMessageOnTerminal(message2);
		}
		mmServerSocket = tmp;
	}
	public void run() {
		BluetoothSocket socket = null;

		CharSequence message = "Start socket listen";
		act.printMessageOnTerminal(message);
		byte[] buffer = new byte[100];  // buffer store for the stream
		int bytes;                       // bytes returned from read()
		// Keep listening until exception occurs or a socket is returned
		while (true) {
			try {
				//socket = mmServerSocket.accept();
				mmServerSocket.connect();
			} catch (IOException e) {
				break;
			}
			// If a connection was accepted
			if (mmServerSocket != null) {
				// Do work to manage the connection (in a separate thread)
				//manageConnectedSocket(socket);
				// but for now do this work in this thread
				
				//CharSequence message5 = "Accepted";
				//act.printMessageOnTerminal(message5);
				
				try {
					inStream = mmServerSocket.getInputStream();
					outStream = mmServerSocket.getOutputStream();
				} catch (IOException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}

				if(inStream != null)
				{
					while(true) {
						try {
							bytes = inStream.read(buffer);

							mHandler.obtainMessage(MainActivity.MESSAGE_READ, bytes, -1, buffer).sendToTarget();
						} catch (IOException e) {
							break;
						}
					}


				} else {
					//toastMessage(R.string.SocketError, Toast.LENGTH_SHORT);
				}

				try {
					mmServerSocket.close();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				break;
			}
//			CharSequence message2 = "accept exception";
//			act.printMessageOnTerminal(message2);
		}
	}

	public void write(byte[] bytes) {

		if(outStream != null) {
			try {
				outStream.write(bytes);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				CharSequence message2 = "write exception";
				act.printMessageOnTerminal(message2);
			}
		}			

	}

	/** Will cancel the listening socket, and cause the thread to finish */
	public void cancel() {
		try {
			mmServerSocket.close();
		} catch (IOException e) { }
	}
	
	
}
