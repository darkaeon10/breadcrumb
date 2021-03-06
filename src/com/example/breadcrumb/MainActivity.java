package com.example.breadcrumb;

import ins.INSController;

import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Semaphore;

import mapping.Mapper;
import mapping.PathMapper;
import android.app.Activity;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.util.Log;
import data.BatchProcessingResults;
import data.SensorEntry;

public class MainActivity extends Activity implements SensorEventListener {

	//Sensor variables
	private SensorManager sensorManager;
	private Sensor senAccelerometer, senGyroscope, senOrientation, senProximity, senMagnetometer;
	
	//Time-related variables
	private long hz = 100;
	private long timeStampOfLastStepDetection = 0;
//	private long timeStampOfLastSensorEntry = 0;
//	private long sensorEntryTimeInterval = 1000/hz * 1000; //nano seconds
	private long stepDetectorTimeInterval = 1000000000;
	private boolean isModifyingSensorEntryBatch;
	private boolean isInPocket;
	
	//INS related
	private INSController insController;
	private Mapper mapper;
	private ArrayList<SensorEntry> sensorEntryBatch;
	private SensorEntry nextSensorEntryToAdd;
	
	private Semaphore sensorEntryMutex = new Semaphore(1);
	
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //setContentView(R.layout.activity_main);
        
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        senAccelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        senGyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        senOrientation = sensorManager.getDefaultSensor(Sensor.TYPE_ORIENTATION); //temporary. not sure how to use the correct one
        senProximity = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);
        
        senMagnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        registerListeners();
        
        sensorEntryBatch = new ArrayList<SensorEntry>();
        insController = new INSController(this);
        mapper = new PathMapper(this);
        nextSensorEntryToAdd = new SensorEntry();
        
        timeStampOfLastStepDetection = System.nanoTime();
        isInPocket = false;
        
        startINS();
    }
    
    protected void onPause() {
        super.onPause();
        sensorManager.unregisterListener(this);
    }

    protected void onResume() {
        super.onResume();
        registerListeners();
    }
    
    protected void onDestroy(){
    	super.onDestroy();
    	
    	MediaPlayer mediaPlayer = MediaPlayer.create(getBaseContext(), R.raw.airhorn);
    	mediaPlayer.start();
    }
    
    private void registerListeners(){
        sensorManager.registerListener(this, senAccelerometer, SensorManager.SENSOR_DELAY_FASTEST);
        sensorManager.registerListener(this, senGyroscope, SensorManager.SENSOR_DELAY_FASTEST);
        sensorManager.registerListener(this, senOrientation, SensorManager.SENSOR_DELAY_FASTEST);
        sensorManager.registerListener(this, senProximity, SensorManager.SENSOR_DELAY_FASTEST);
        sensorManager.registerListener(this, senMagnetometer, SensorManager.SENSOR_DELAY_FASTEST);
    }

	@Override
	public void onAccuracyChanged(Sensor sensor, int arg1) {
		// TODO Auto-generated method stub
		
	}
	
	private void startINS(){
		Timer recordTimer = new Timer();
		recordTimer.scheduleAtFixedRate(new TimerTask() {          
            @Override
            public void run() {
                recordSensorEntry();
            }
		}, 0, 10);
            
//		Timer processTimer = new Timer();
//		processTimer.scheduleAtFixedRate(new TimerTask() {          
//            @Override
//            public void run() {
//                processSensorEntries();
//            }
//            
//		}, 0, 1000);
	}
	    
	/*
	 * Records the SensorEntry object named nextSensorEntryToAdd. 
	 * Adds the object to the batch list, and then initializes a new empty SensorEntry for the next one.
	 */
	private void recordSensorEntry(){
		//if(!isModifyingSensorEntryBatch){
			this.nextSensorEntryToAdd.setTimeRecorded(System.nanoTime());
			this.nextSensorEntryToAdd.buildSensorList();
			
			sensorEntryBatch.add(this.nextSensorEntryToAdd);
			this.nextSensorEntryToAdd = new SensorEntry();
		//}
	}
	
	/*
	 * Runs the batch of sensor entries through the INS
	 */
	private void processSensorEntries(){
		/*
    	 * For now, the sensor entries are being processed by the INS.
    	 * In the future, insert the phone mode detection here and just switch to INS or V-INS accordingly.
    	 */
		
		int batchSize = this.sensorEntryBatch.size();
		
		if(batchSize >= 100){
			if(true){
			//just some race condition flag so that recordEntries won't concurrently modify the sensorEntryBatch
			isModifyingSensorEntryBatch = true;
			
			//get only the first 100 
			// this is where concurrency dies
			ArrayList<SensorEntry> toProcess = new ArrayList<SensorEntry>(this.sensorEntryBatch.subList(0, 100));
			
			//retain the next sensor entries as part of the next batch to process
			this.sensorEntryBatch = new ArrayList<SensorEntry>(this.sensorEntryBatch.subList(100, batchSize));
			
			//just some race condition flag so that recordEntries won't concurrently modify the sensorEntryBatch
			isModifyingSensorEntryBatch = false; //just some race condition flag
			
			
			//process the 100 entries
			BatchProcessingResults results = this.insController.processSensorEntryBatch(toProcess);
		
	
			//feed the results to the mapping module
			mapper.plot(this, results);}
			else{
				isModifyingSensorEntryBatch = true;
				
				//retain the next sensor entries as part of the next batch to process
				this.sensorEntryBatch = new ArrayList<SensorEntry>(this.sensorEntryBatch.subList(100, batchSize));
				
				//just some race condition flag so that recordEntries won't concurrently modify the sensorEntryBatch
				isModifyingSensorEntryBatch = false; //just some race condition flag
			}
				
		}
	}

	/* 
	 * Called when there are changes in sensor readings.
	 * Simply updates the SensorEntry object representing the next SensorEntry to be recorded.
	 * 
	 * (non-Javadoc)
	 * @see android.hardware.SensorEventListener#onSensorChanged(android.hardware.SensorEvent)
	 */
	@Override
	public void onSensorChanged(SensorEvent sensorEvent) {
		// TODO Auto-generated method stub
		Sensor mySensor = sensorEvent.sensor;
		 
	    if (mySensor.getType() == Sensor.TYPE_ACCELEROMETER) {
	    	
	    	//add to the sensor entry batch if time to add

    		float x = sensorEvent.values[0];
	        float y = sensorEvent.values[1];
	        float z = sensorEvent.values[2];
	    	
	    	nextSensorEntryToAdd.setAcc_x(x);
	    	nextSensorEntryToAdd.setAcc_y(y);
	    	nextSensorEntryToAdd.setAcc_z(z);
	    
	    }
	    else if (mySensor.getType() == Sensor.TYPE_GYROSCOPE) {
	    	
	    	//add to the sensor entry batch if time to add

    		float x = sensorEvent.values[0];
	        float y = sensorEvent.values[1];
	        float z = sensorEvent.values[2];
	    	
	    	nextSensorEntryToAdd.setGyro_x(x);
	    	nextSensorEntryToAdd.setGyro_y(y);
	    	nextSensorEntryToAdd.setGyro_z(z);
	    }
	    else if (mySensor.getType() == Sensor.TYPE_ORIENTATION) {
	    	
	    	//add to the sensor entry batch if time to add

    		float x = sensorEvent.values[0];
	        float y = sensorEvent.values[1];
	        float z = sensorEvent.values[2];
	    	
	    	nextSensorEntryToAdd.setOrient_x(x);
	    	nextSensorEntryToAdd.setOrient_y(y);
	    	nextSensorEntryToAdd.setOrient_z(z);
	    	
	    	//PathMapper.debug("" + String.format("%.2f", x) + " " +String.format("%.2f", y) + " " + String.format("%.2f", z) );
	    }
	    else if (mySensor.getType() == Sensor.TYPE_PROXIMITY){
	    	String str = "";
	    	str += sensorEvent.values[0] + " ";
	    	PathMapper.debug(str);
		    Log.e("testy", "distance: " + str);
		    if(sensorEvent.values[0] > 0)
		    	isInPocket = false;
		    else 
		    	isInPocket = true;
	    }
	    
	    
	    
//	    long currTime = System.nanoTime();
	    if(/*currTime - this.timeStampOfLastStepDetection >= this.stepDetectorTimeInterval || */this.sensorEntryBatch.size() >= 100){
//	    	this.timeStampOfLastStepDetection = currTime;
	    	processSensorEntries();
	    }
	}
}
