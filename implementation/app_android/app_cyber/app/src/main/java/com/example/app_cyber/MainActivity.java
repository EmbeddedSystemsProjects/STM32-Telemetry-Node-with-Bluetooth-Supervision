/*
file MainActivity.java --This file is the main core of our app_cyber project. It gives us all the main function and
utilities for our application.
 */
/* ========================================================================= */
/* IMPORT of ANDROID LIBRARIES and THIRD PART                            */
/* ========================================================================= */
package com.example.app_cyber;

//Runtime permission management (essential for Bluetooth in modern versions of Android)
import android.Manifest;
import android.content.pm.PackageManager;
import androidx.core.app.ActivityCompat;
// Android Bluetooth Classic API for connecting to the HC-05 module
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothSocket;
// System components and concurrency management (UI Thread vs. Background Thread)
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
// User interface (UI) widget for the telemetry dashboard
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;
// Android Activity lifecycle framework
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
// Third-party library (MPAndroidChart) for real-time chart plotting -->github
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.ValueFormatter;
// Standard Java utilities for I/O streams, data collections, dates, and UUIDs
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import android.app.DatePickerDialog;
import android.graphics.Color;

/*
 * @class MainActivity
 * @brief Main Activity of the Android application.
 * Handles authentication, the Bluetooth Classic connection to the HC-05 module,
 * asynchronous data acquisition, local persistence, and real-time graphical plotting.
 */
public class MainActivity extends AppCompatActivity {
    //standard UUID for SPP(Serial Port Profile) utilized by HC-05
    private static final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private static final int PERMISSION_REQUEST_CODE = 101;
    private static final int WINDOW_SIZE = 25;
    // Database manager
    private DatabaseHelper dbHelper;
    //state variable and bluetooth connection
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothSocket bluetoothSocket;
    private InputStream inputStream;
    private volatile boolean isConnected = false;
    private boolean isChartPaused = false;
    // Logical sections of the interface (navigation layout / screens)
    private View loginSection, deviceSection, dashboardSection, historySection;
    private EditText etUsername, etPassword, etHistoryDate;
    private TextView tvLoginError, tvStatus, tvTimestamp, tvLiveValue, tvLiveTitle;
    // Drop-down menu (Spinner) for selecting modes and metrics
    private Spinner spinnerMode;
    private Spinner spinnerAxisLive;
    private Spinner spinnerSensorHistory, spinnerAxisHistory;
    // Control buttons and advanced visualization widgets
    private Button btnConnect, btnPauseChart, btnResetZoom, btnDisconnect;
    private LineChart liveChart, historyChart;
    private TableLayout tableHistory;
    // Lists of coordinates (Entries) for real-time chart rendering
    private final ArrayList<Entry> entriesTemp = new ArrayList<>();
    private final ArrayList<Entry> entriesAccelX = new ArrayList<>();
    private final ArrayList<Entry> entriesAccelY = new ArrayList<>();
    private final ArrayList<Entry> entriesAccelZ = new ArrayList<>();
    // Lists for loading historical records from the local database
    private List<Ds18b20Record> loadedDsRecords = new ArrayList<>();
    private List<Gy521Record> loadedGyRecords = new ArrayList<>();
    // Current selectors for the displayed metrics
    private String selectedMetricLive = "temp";
    private String selectedMetricHistory = "temp";
    // Handler associated with the Main Looper for the safe execution of tasks on the UI thread
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);// Associate the XML layout with the Activity
        // Initialization of the local SQLite database manager
        dbHelper = new DatabaseHelper(this);

        // Call to the modular configuration methods for the interface and peripherals
        initViews();
        setupSpinners();
        setupCharts();
        setupListeners();
        initBluetoothAdapter();
    }
/*
@brief Initializes the Bluetooth adapter.
 */
    private void initBluetoothAdapter() {
        // Attempt to retrieve the BluetoothAdapter via the modern system service
        BluetoothManager bluetoothManager = getSystemService(BluetoothManager.class);
        if (bluetoothManager != null) {
            bluetoothAdapter = bluetoothManager.getAdapter();
        } else {
            // Fallback method for older versions of the Android framework
            bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        }
    }

    /*
    @brief Initializes the views from the XML layout.
     */
    private void initViews() {
        // Views from the XML layout
        loginSection = findViewById(R.id.loginSection);
        deviceSection = findViewById(R.id.deviceSection);
        dashboardSection = findViewById(R.id.dashboardSection);
        historySection = findViewById(R.id.historySection);
        //Text fields for username and password
        etUsername = findViewById(R.id.etUsername);
        etPassword = findViewById(R.id.etPassword);
        tvLoginError = findViewById(R.id.tvLoginError);
        // Text fields for selecting date
        etHistoryDate = findViewById(R.id.etHistoryDate);
        spinnerMode = findViewById(R.id.spinnerMode);
        // Text fields for displaying live data
        tvLiveTitle = findViewById(R.id.tvLiveTitle);
        spinnerAxisLive = findViewById(R.id.spinnerAxisLive);
        spinnerSensorHistory = findViewById(R.id.spinnerSensorHistory);
        spinnerAxisHistory = findViewById(R.id.spinnerAxisHistory);
        // Text fields for displaying status
        tvStatus = findViewById(R.id.tvStatus);
        tvTimestamp = findViewById(R.id.tvTimestamp);
        tvLiveValue = findViewById(R.id.tvLiveValue);
        // Buttons for controlling the connection and chart
        btnConnect = findViewById(R.id.btnConnect);
        btnPauseChart = findViewById(R.id.btnPauseChart);
        btnResetZoom = findViewById(R.id.btnResetZoom);
        btnDisconnect = findViewById(R.id.btnDisconnect);
        // Chart views
        liveChart = findViewById(R.id.liveChart);
        historyChart = findViewById(R.id.historyChart);
        tableHistory = findViewById(R.id.tableHistory);
    }

    /*
    @brief Sets up the spinners for selecting modes and metrics.
     */
    private void setupSpinners() {
        // Main Menu "sensor node" with the 3 actual options to select
        String[] modes = {
                "Telemetria Live: DS18B20 (Temperatura)",
                "Telemetria Live: GY-521 (Accelerometro)",
                "Visualizza Storico (Database)"
        };
        ArrayAdapter<String> modeAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, modes);
        spinnerMode.setAdapter(modeAdapter);

        // Menu "sensor history" with the 2 actual options to select
        String[] sensors = {"Sensore DS18B20 (Temperatura)", "Sensore GY-521 (Accelerometro)"};
        ArrayAdapter<String> sensorAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, sensors);
        spinnerSensorHistory.setAdapter(sensorAdapter);

        // Menu axes utilitized for the live chart and history chart of GY-521
        String[] axes = {"Accelerazione Asse X", "Accelerazione Asse Y", "Accelerazione Asse Z"};
        ArrayAdapter<String> axisAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, axes);
        spinnerAxisLive.setAdapter(axisAdapter);
        spinnerAxisHistory.setAdapter(axisAdapter);
    }

    /*
    @brief Sets up the charts for displaying live and historical data.
     */
    private void setupCharts() {
        // Live Chart configuration
        liveChart.getDescription().setEnabled(false);
        liveChart.setTouchEnabled(true);
        liveChart.setDragEnabled(true);
        liveChart.setScaleEnabled(true);
        liveChart.setPinchZoom(true);
        // X-axis configuration
        XAxis xAxisLive = liveChart.getXAxis();
        xAxisLive.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxisLive.setGranularity(1f);
        xAxisLive.setValueFormatter(new ValueFormatter() {
            // Formatter for the X-axis values (seconds)
            @Override
            public String getFormattedValue(float value) {
                return String.format(Locale.getDefault(), "%.1f s", value);
            }
        });
        // Y-axis configuration
        liveChart.getAxisLeft().setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return String.format(Locale.getDefault(), "%.1f", value);
            }
        });
        liveChart.getAxisRight().setEnabled(false);
        // Data configuration
        LineDataSet dataSetLive = new LineDataSet(new ArrayList<>(), "Valore Live");
        dataSetLive.setColor(Color.parseColor("#2980B9"));
        dataSetLive.setLineWidth(2f);
        dataSetLive.setDrawCircles(false);
        dataSetLive.setDrawValues(false);
        // Set the data for the live chart
        liveChart.setData(new LineData(dataSetLive));
        // History Chart configuration
        historyChart.getDescription().setEnabled(false);
        historyChart.setTouchEnabled(true);
        historyChart.setDragEnabled(true);
        historyChart.setScaleEnabled(true);
        historyChart.setPinchZoom(true);
        // X-axis configuration
        XAxis xAxisHistory = historyChart.getXAxis();
        xAxisHistory.setPosition(XAxis.XAxisPosition.BOTTOM);
        // Formatter for the X-axis values (seconds)
        historyChart.getAxisLeft().setValueFormatter(new ValueFormatter() {
            // Formatter for the X-axis values (seconds)
            @Override
            public String getFormattedValue(float value) {
                return String.format(Locale.getDefault(), "%.1f", value);
            }
        });
        historyChart.getAxisRight().setEnabled(false);
        // Data configuration
        LineDataSet dataSetHistory = new LineDataSet(new ArrayList<>(), "Storico");
        dataSetHistory.setColor(Color.parseColor("#27AE60"));
        dataSetHistory.setLineWidth(2f);
        dataSetHistory.setDrawCircles(false);
        dataSetHistory.setDrawValues(false);
        dataSetHistory.setMode(LineDataSet.Mode.CUBIC_BEZIER);
        historyChart.setData(new LineData(dataSetHistory));
    }
    /*
    @brief Adds a new point to the live chart.
     */
    private void addLivePoint(float sec, float value) {
        runOnUiThread(() -> {
            LineData data = liveChart.getData();
            // Check if data is not null
            if (data != null) {
                LineDataSet dataSet = (LineDataSet) data.getDataSetByIndex(0);
                // Check if dataSet is not null
                if (dataSet != null) {
                    data.addEntry(new Entry(sec, value), 0);// Add new point to the live chart
                    data.notifyDataChanged();// Notify the data has changed
                    liveChart.notifyDataSetChanged();// Notify the chart that data has changed
                    liveChart.setVisibleXRangeMaximum(WINDOW_SIZE);// Adjust the X-axis range
                    liveChart.moveViewToX(sec);// Move the chart to the new point
                    liveChart.invalidate(); // Refresh the chart
                }
            }
        });
    }

    /*
    @brief Sets up the listeners for the buttons and controls.
     */
    private void setupListeners() {
        findViewById(R.id.btnLogin).setOnClickListener(v -> {
            String u = etUsername.getText().toString();
            String p = etPassword.getText().toString();
            if ("admin".equals(u) && "admin".equals(p)) {
                loginSection.setVisibility(View.GONE);
                deviceSection.setVisibility(View.VISIBLE);
                tvLoginError.setVisibility(View.GONE);
            } else {
                tvLoginError.setVisibility(View.VISIBLE);
            }
        });

        // Button for selecting the mode of operation
        findViewById(R.id.btnStartMode).setOnClickListener(v -> {
            int selectedIndex = spinnerMode.getSelectedItemPosition();
            deviceSection.setVisibility(View.GONE);

            if (selectedIndex == 0) {
                //option 0: Telemetria Live DS18B20
                dashboardSection.setVisibility(View.VISIBLE);
                tvLiveTitle.setText("Telemetria Live: DS18B20");
                spinnerAxisLive.setVisibility(View.GONE);
                selectedMetricLive = "temp";
                updateActiveChartDataset();

            } else if (selectedIndex == 1) {
                // Option 1: Telemetria Live GY-521
                dashboardSection.setVisibility(View.VISIBLE);
                tvLiveTitle.setText("Telemetria Live: GY-521");
                spinnerAxisLive.setVisibility(View.VISIBLE);
                updateMetricFromAxisLive(spinnerAxisLive.getSelectedItemPosition());
                updateActiveChartDataset();

            } else {
                // Option 2: Storico Database
                historySection.setVisibility(View.VISIBLE);
                if (etHistoryDate.getText().toString().isEmpty()) {
                    String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
                    etHistoryDate.setText(today);
                }
            }
        });
        // Button for selecting the date of the history
        etHistoryDate.setOnClickListener(v -> {
            Calendar cal = Calendar.getInstance();
            DatePickerDialog dialog = new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
                String dateStr = String.format(Locale.getDefault(), "%04d-%02d-%02d", year, month + 1, dayOfMonth);
                etHistoryDate.setText(dateStr);
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH));
            dialog.show();
        });
        // Spinner for selecting the axis of the live chart
        spinnerAxisLive.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                updateMetricFromAxisLive(position);
                updateActiveChartDataset();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
        // Spinner for selecting the sensor of the history chart
        spinnerSensorHistory.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position == 0) {
                    spinnerAxisHistory.setVisibility(View.GONE);
                    selectedMetricHistory = "temp";
                } else {
                    spinnerAxisHistory.setVisibility(View.VISIBLE);
                    updateMetricFromAxisHistory(spinnerAxisHistory.getSelectedItemPosition());
                }
                updateHistoryChart();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
        // Spinner for selecting the axis of the history chart
        spinnerAxisHistory.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                updateMetricFromAxisHistory(position);
                updateHistoryChart();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
        // Buttons for controlling the connection and chart
        btnConnect.setOnClickListener(v -> checkPermissionsAndConnect());
        btnDisconnect.setOnClickListener(v -> disconnectBluetooth());
        // Button for pausing the chart
        btnPauseChart.setOnClickListener(v -> {
            isChartPaused = !isChartPaused;
            if (isChartPaused) {
                btnPauseChart.setText(R.string.btn_resume_chart);
                btnPauseChart.setBackgroundColor(Color.parseColor("#27AE60"));
            } else {
                btnPauseChart.setText(R.string.btn_pause_chart);
                btnPauseChart.setBackgroundColor(Color.parseColor("#F39C12"));
            }
        });
        // Button for resetting the zoom
        btnResetZoom.setOnClickListener(v -> {
            liveChart.fitScreen();
            liveChart.invalidate();
        });
        // Button for disconnecting from the HC-05 module
        findViewById(R.id.btnBackLive).setOnClickListener(v -> {
            disconnectBluetooth();
            dashboardSection.setVisibility(View.GONE);
            deviceSection.setVisibility(View.VISIBLE);
        });
        // Button for loading historical data
        findViewById(R.id.btnLoadHistory).setOnClickListener(v -> loadHistoryData());
        // Button for resetting the zoom in the history chart
        findViewById(R.id.btnResetZoomHistory).setOnClickListener(v -> {
            historyChart.fitScreen();
            historyChart.invalidate();
        });
        // Button for navigating back to the device selection
        findViewById(R.id.btnBackHistory).setOnClickListener(v -> {
            historySection.setVisibility(View.GONE);
            deviceSection.setVisibility(View.VISIBLE);
        });
    }
    /*
    @brief Updates the selected metric based on the axis index.
     */
    private void updateMetricFromAxisLive(int axisIndex) {
        switch (axisIndex) {
            case 1: selectedMetricLive = "accelY"; break;
            case 2: selectedMetricLive = "accelZ"; break;
            default: selectedMetricLive = "accelX"; break;
        }
    }
    /*
    @brief Updates the selected metric based on the axis index.
     */
    private void updateMetricFromAxisHistory(int axisIndex) {
        switch (axisIndex) {
            case 1: selectedMetricHistory = "accelY"; break;
            case 2: selectedMetricHistory = "accelZ"; break;
            default: selectedMetricHistory = "accelX"; break;
        }
    }
    /*
    @brief Checks the permissions and connects to the HC-05 module.
     */
    private void checkPermissionsAndConnect() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.BLUETOOTH_CONNECT}, PERMISSION_REQUEST_CODE);
                return;
            }
        }
        startBluetoothConnection();
    }
    /*
    @brief Handles the result of the permission request.
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startBluetoothConnection();
            } else {
                Toast.makeText(this, R.string.toast_bluetooth_permission_required, Toast.LENGTH_SHORT).show();
            }
        }
    }
    /*
    @brief Starts the Bluetooth connection process.
     */
    private void startBluetoothConnection() {
        if (bluetoothAdapter == null) {
            Toast.makeText(this, R.string.toast_bluetooth_not_supported, Toast.LENGTH_SHORT).show();
            return;
        }

        if (!bluetoothAdapter.isEnabled()) {
            Toast.makeText(this, R.string.toast_enable_bluetooth, Toast.LENGTH_SHORT).show();
            return;
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
                && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return;
        }

        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
        BluetoothDevice targetDevice = null;

        if (pairedDevices != null) {
            for (BluetoothDevice device : pairedDevices) {
                if ("HC-05".equalsIgnoreCase(device.getName()) || "HC-06".equalsIgnoreCase(device.getName())) {
                    targetDevice = device;
                    break;
                }
            }
        }

        if (targetDevice == null) {
            Toast.makeText(this, R.string.toast_hc05_not_found, Toast.LENGTH_LONG).show();
            return;
        }

        final BluetoothDevice finalTargetDevice = targetDevice;
        tvStatus.setText(R.string.status_connecting);

        new Thread(() -> {
            try {
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
                        || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                    bluetoothSocket = finalTargetDevice.createRfcommSocketToServiceRecord(SPP_UUID);
                    bluetoothSocket.connect();
                    inputStream = bluetoothSocket.getInputStream();
                    isConnected = true;

                    mainHandler.post(() -> {
                        tvStatus.setText(R.string.status_connected);
                        tvStatus.setTextColor(Color.parseColor("#27AE60"));
                        btnConnect.setEnabled(false);
                        btnPauseChart.setEnabled(true);
                        btnDisconnect.setEnabled(true);
                    });

                    listenForData();
                }
            } catch (IOException e) {
                mainHandler.post(() -> {
                    tvStatus.setText(R.string.status_error);
                    tvStatus.setTextColor(Color.parseColor("#E74C3C"));
                });
                disconnectBluetooth();
            }
        }).start();
    }
    /*
    @brief Listens for incoming data from the HC-05 module.
     */
    private void listenForData() {
        byte[] buffer = new byte[1024];
        StringBuilder stringBuilder = new StringBuilder();

        while (isConnected) {
            try {
                int bytes = inputStream.read(buffer);
                if (bytes > 0) {
                    stringBuilder.append(new String(buffer, 0, bytes));
                    int lineEnd = stringBuilder.indexOf("\n");
                    while (lineEnd != -1) {
                        String line = stringBuilder.substring(0, lineEnd).trim();
                        stringBuilder.delete(0, lineEnd + 1);
                        processCSVLine(line);
                        lineEnd = stringBuilder.indexOf("\n");
                    }
                }
            } catch (IOException e) {
                break;
            }
        }
    }
    /*
    @brief Processes a single line of CSV data from the HC-05 module.
     */
    private void processCSVLine(String line) {
        String[] parts = line.split(",");
        if (parts.length >= 5) {
            try {
                long timestampMs = Long.parseLong(parts[0]);
                float temp = Float.parseFloat(parts[1]);
                float ax = Float.parseFloat(parts[2]);
                float ay = Float.parseFloat(parts[3]);
                float az = Float.parseFloat(parts[4]);

                float sec = timestampMs / 1000f;
                String isoCreatedAt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());

                boolean isTempValid = (temp > -900.0f);

                if (isTempValid) {
                    dbHelper.insertDs18b20(timestampMs, temp, isoCreatedAt);
                }
                dbHelper.insertGy521(timestampMs, ax, ay, az, isoCreatedAt);

                mainHandler.post(() -> {
                    if (isTempValid) {
                        entriesTemp.add(new Entry(sec, temp));
                    }
                    entriesAccelX.add(new Entry(sec, ax));
                    entriesAccelY.add(new Entry(sec, ay));
                    entriesAccelZ.add(new Entry(sec, az));

                    if (entriesAccelX.size() > 2000) {
                        if (!entriesTemp.isEmpty()) entriesTemp.remove(0);
                        entriesAccelX.remove(0);
                        entriesAccelY.remove(0);
                        entriesAccelZ.remove(0);
                    }

                    String timeStr = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
                    tvTimestamp.setText("Orario Acquisizione: " + timeStr);

                    float activeVal = 0.0f;
                    boolean canPlotChart = true;

                    switch (selectedMetricLive) {
                        case "temp":
                            if (isTempValid) {
                                activeVal = temp;
                                tvLiveValue.setText(String.format(Locale.getDefault(), "%.2f °C", temp));
                                tvLiveValue.setTextColor(Color.parseColor("#2C3E50"));
                            } else {
                                tvLiveValue.setText("N/A (Scollegato)");
                                tvLiveValue.setTextColor(Color.parseColor("#E74C3C"));
                                canPlotChart = false;
                            }
                            break;
                        case "accelX":
                            activeVal = ax;
                            tvLiveValue.setText(String.format(Locale.getDefault(), "%.2f g", ax));
                            tvLiveValue.setTextColor(Color.parseColor("#2C3E50"));
                            break;
                        case "accelY":
                            activeVal = ay;
                            tvLiveValue.setText(String.format(Locale.getDefault(), "%.2f g", ay));
                            tvLiveValue.setTextColor(Color.parseColor("#2C3E50"));
                            break;
                        case "accelZ":
                            activeVal = az;
                            tvLiveValue.setText(String.format(Locale.getDefault(), "%.2f g", az));
                            tvLiveValue.setTextColor(Color.parseColor("#2C3E50"));
                            break;
                    }

                    if (!isChartPaused && canPlotChart) {
                        addLivePoint(sec, activeVal);
                    }
                });

            } catch (NumberFormatException ignored) {}
        }
    }
    /*
    @brief Updates the active chart dataset.
     */
    private void updateActiveChartDataset() {
        ArrayList<Entry> currentEntries;
        String label;

        switch (selectedMetricLive) {
            case "accelX": currentEntries = entriesAccelX; label = "Accelerazione X (g)"; break;
            case "accelY": currentEntries = entriesAccelY; label = "Accelerazione Y (g)"; break;
            case "accelZ": currentEntries = entriesAccelZ; label = "Accelerazione Z (g)"; break;
            default: currentEntries = entriesTemp; label = "Temperatura (°C)"; break;
        }

        LineDataSet dataSet = new LineDataSet(new ArrayList<>(currentEntries), label);
        dataSet.setColor(Color.parseColor("#2980B9"));
        dataSet.setLineWidth(2f);
        dataSet.setDrawCircles(false);

        liveChart.setData(new LineData(dataSet));

        if (currentEntries.size() > WINDOW_SIZE) {
            float minX = currentEntries.get(currentEntries.size() - WINDOW_SIZE).getX();
            float maxX = currentEntries.get(currentEntries.size() - 1).getX();
            liveChart.getXAxis().setAxisMinimum(minX);
            liveChart.getXAxis().setAxisMaximum(maxX);
        }

        liveChart.notifyDataSetChanged();
        liveChart.invalidate();
    }
    /*
    @brief Loads historical data from the local database.
     */
    private void loadHistoryData() {
        String selectedDate = etHistoryDate.getText().toString().trim();
        if (selectedDate.isEmpty()) {
            Toast.makeText(this, "Seleziona prima una data!", Toast.LENGTH_SHORT).show();
            return;
        }

        int sensorType = spinnerSensorHistory.getSelectedItemPosition();

        if (sensorType == 0) {
            loadedDsRecords = dbHelper.getDs18b20ByDate(selectedDate);
            loadedGyRecords.clear();

            if (loadedDsRecords.isEmpty()) {
                Toast.makeText(this, "Nessun dato DS18B20 trovato per questa data.", Toast.LENGTH_SHORT).show();
                historyChart.clear();
                historyChart.invalidate();
                clearTableData();
                return;
            }
        } else {
            loadedGyRecords = dbHelper.getGy521ByDate(selectedDate);
            loadedDsRecords.clear();

            if (loadedGyRecords.isEmpty()) {
                Toast.makeText(this, "Nessun dato GY-521 trovato per questa data.", Toast.LENGTH_SHORT).show();
                historyChart.clear();
                historyChart.invalidate();
                clearTableData();
                return;
            }
        }

        updateHistoryChart();
        populateHistoryTable();
    }
    /*
    @brief Updates the history chart dataset.
     */
    private void updateHistoryChart() {
        int sensorType = spinnerSensorHistory.getSelectedItemPosition();
        ArrayList<Entry> entries = new ArrayList<>();
        String label = "";

        if (sensorType == 0) {
            if (loadedDsRecords == null || loadedDsRecords.isEmpty()) {
                historyChart.clear();
                historyChart.invalidate();
                return;
            }
            label = "Temperatura (°C)";

            float cumulativeSec = 0f;
            long lastRawTime = -1;

            for (Ds18b20Record record : loadedDsRecords) {
                if (record.temperature > -900.0f && record.temperature > 1.0f) {
                    if (lastRawTime == -1) {
                        cumulativeSec = 0f; // Primo punto di partenza
                    } else {
                        long delta = record.timestampMs - lastRawTime;
                        if (delta >= 0) {
                            cumulativeSec += (delta / 1000f); // Incremento normale del tempo
                        } else {
                            // Rilevato reset di HAL_GetTick(): la scheda si è riavviata!
                            // Invece di tornare indietro, aggiungiamo un piccolo offset fittizio (es. 1 secondo)
                            cumulativeSec += 1.0f;
                        }
                    }
                    lastRawTime = record.timestampMs;
                    entries.add(new Entry(cumulativeSec, record.temperature));
                }
            }
        } else {
            if (loadedGyRecords == null || loadedGyRecords.isEmpty()) {
                historyChart.clear();
                historyChart.invalidate();
                return;
            }
            switch (selectedMetricHistory) {
                case "accelY": label = "Accelerazione Y (g)"; break;
                case "accelZ": label = "Accelerazione Z (g)"; break;
                default: label = "Accelerazione X (g)"; break;
            }

            float cumulativeSec = 0f;
            long lastRawTime = -1;

            for (Gy521Record record : loadedGyRecords) {
                if (lastRawTime == -1) {
                    cumulativeSec = 0f;
                } else {
                    long delta = record.timestampMs - lastRawTime;
                    if (delta >= 0) {
                        cumulativeSec += (delta / 1000f);
                    } else {
                        // Reset hardware gestito
                        cumulativeSec += 1.0f;
                    }
                }
                lastRawTime = record.timestampMs;

                float val = 0f;
                switch (selectedMetricHistory) {
                    case "accelY": val = record.accelY; break;
                    case "accelZ": val = record.accelZ; break;
                    default: val = record.accelX; break;
                }
                entries.add(new Entry(cumulativeSec, val));
            }
        }

        if (entries.isEmpty()) {
            historyChart.clear();
            historyChart.invalidate();
            return;
        }

        LineDataSet dataSet = new LineDataSet(entries, label);
        dataSet.setColor(Color.parseColor("#27AE60"));
        dataSet.setLineWidth(2f);
        dataSet.setDrawCircles(false);
        dataSet.setDrawValues(false);

        historyChart.setData(new LineData(dataSet));
        historyChart.notifyDataSetChanged();
        historyChart.invalidate();
    }
    /*
    @brief Populates the history table with the loaded data.
     */
    private void populateHistoryTable() {
        clearTableData();
        int sensorType = spinnerSensorHistory.getSelectedItemPosition();

        if (sensorType == 0) {
            if (loadedDsRecords == null || loadedDsRecords.isEmpty()) return;

            int totalSize = loadedDsRecords.size();
            int maxDisplay = 300; // Limite sicuro per non rallentare la UI
            int step = Math.max(1, totalSize / maxDisplay); // Calcola il passo di campionamento

            for (int i = 0; i < totalSize; i += step) {
                Ds18b20Record r = loadedDsRecords.get(i);
                TableRow row = new TableRow(this);
                String timeOnly = r.createdAt.contains(" ") ? r.createdAt.split(" ")[1] : r.createdAt;
                String tempStr = (r.temperature > -900.0f) ? String.format(Locale.getDefault(), "%.2f", r.temperature) : "N/A";

                row.addView(createCell(timeOnly));
                row.addView(createCell(tempStr));
                row.addView(createCell("-"));
                row.addView(createCell("-"));
                row.addView(createCell("-"));
                tableHistory.addView(row);
            }
        } else {
            if (loadedGyRecords == null || loadedGyRecords.isEmpty()) return;

            int totalSize = loadedGyRecords.size();
            int maxDisplay = 300;
            int step = Math.max(1, totalSize / maxDisplay);

            for (int i = 0; i < totalSize; i += step) {
                Gy521Record r = loadedGyRecords.get(i);
                TableRow row = new TableRow(this);
                String timeOnly = r.createdAt.contains(" ") ? r.createdAt.split(" ")[1] : r.createdAt;

                row.addView(createCell(timeOnly));
                row.addView(createCell("-"));
                row.addView(createCell(String.format(Locale.getDefault(), "%.2f", r.accelX)));
                row.addView(createCell(String.format(Locale.getDefault(), "%.2f", r.accelY)));
                row.addView(createCell(String.format(Locale.getDefault(), "%.2f", r.accelZ)));
                tableHistory.addView(row);
            }
        }
    }
    /*
    @brief Creates a TextView with the given text.
     */
    private TextView createCell(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setPadding(16, 12, 16, 12);
        return tv;
    }
    /*
    @brief Clears the table data.
     */
    private void clearTableData() {
        int childCount = tableHistory.getChildCount();
        if (childCount > 1) {
            tableHistory.removeViews(1, childCount - 1);
        }
    }
    /*
    @brief Disconnects from the HC-05 module.
     */
    private void disconnectBluetooth() {
        isConnected = false;
        try {
            if (inputStream != null) inputStream.close();
            if (bluetoothSocket != null) bluetoothSocket.close();
        } catch (IOException ignored) {}

        mainHandler.post(() -> {
            tvStatus.setText(R.string.status_disconnected);
            tvStatus.setTextColor(Color.parseColor("#333333"));
            btnConnect.setEnabled(true);
            btnPauseChart.setEnabled(false);
            btnDisconnect.setEnabled(false);
            tvLiveValue.setText(R.string.placeholder_dash);
        });
    }
    /*
    @brief Handles the termination of the application.
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        disconnectBluetooth();
    }
}