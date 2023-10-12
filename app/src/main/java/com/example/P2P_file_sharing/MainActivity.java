package com.example.P2P_file_sharing;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import static android.content.ContentValues.TAG;
import static android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import pub.devrel.easypermissions.AfterPermissionGranted;
import pub.devrel.easypermissions.EasyPermissions;

public class MainActivity extends AppCompatActivity {
    private final int READ_STORAGE_PERMISSION_REQUEST = 1;
    private final int REQUEST_LOCATION_PERMISSION = 1;
    int flag = 0;

    TextView connectionStatus, dataTextView, fileNameTextView;
    Button discoverButton, selectFileButton;
    ImageButton sendFileButton;
    ListView listView;

    WifiP2pManager manager;
    WifiP2pManager.Channel channel;

    BroadcastReceiver receiver;
    IntentFilter intentFilter;

    List<WifiP2pDevice> peers = new ArrayList<>();
    String[] deviceNameArray;
    WifiP2pDevice[] deviceArray;

    Socket socket;

    ServerClass serverClass;
    ClientClass clientClass;
    boolean isHost;
    File file;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        if (Build.VERSION.SDK_INT >= 30) {
            if (!Environment.isExternalStorageManager()) {
                Intent getpermission = new Intent();
                getpermission.setAction(ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                startActivity(getpermission);
            }
        }
        else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                // Permission is not granted, request it
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, READ_STORAGE_PERMISSION_REQUEST);
            }
        }
        requestLocationPermission();
        requestStoragePermission();
        initialWork();
        exqListener();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this);
    }

    @AfterPermissionGranted(REQUEST_LOCATION_PERMISSION)
    public void requestLocationPermission() {
        String perms = Manifest.permission.ACCESS_FINE_LOCATION;

        if(EasyPermissions.hasPermissions(this, perms)) {
            //Toast.makeText(this, "Permission already granted", Toast.LENGTH_SHORT).show();
        }
        else {
            EasyPermissions.requestPermissions(this, "Please grant the location permission", REQUEST_LOCATION_PERMISSION, perms);
        }
    }

    @AfterPermissionGranted(REQUEST_LOCATION_PERMISSION)
    public void requestNearbyDevicePermission() {
        String perms = Manifest.permission.NEARBY_WIFI_DEVICES;

        if(EasyPermissions.hasPermissions(this, perms)) {
            //Toast.makeText(this, "Permission already granted", Toast.LENGTH_SHORT).show();
        }
        else {
            EasyPermissions.requestPermissions(this, "Please grant the nearby devices permission", REQUEST_LOCATION_PERMISSION, perms);
        }
    }

    @AfterPermissionGranted(READ_STORAGE_PERMISSION_REQUEST)
    public void requestStoragePermission() {
        String perms = Manifest.permission.READ_EXTERNAL_STORAGE;
        if(EasyPermissions.hasPermissions(this, perms)) {
            //Toast.makeText(this, "Permission already granted", Toast.LENGTH_SHORT).show();
        }
        else {
            EasyPermissions.requestPermissions(this, "Please grant the storage permission", READ_STORAGE_PERMISSION_REQUEST, perms);
        }
    }

    public void exqListener(){

        discoverButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                try{
                    manager.discoverPeers(channel, new WifiP2pManager.ActionListener() {
                        @Override
                        public void onSuccess() {
                            connectionStatus.setText("Discovery Started");
                        }

                        @Override
                        public void onFailure(int i) {
                            connectionStatus.setText("Discovery Start Failed");
                            Log.e(TAG, "Error code is " + i);
                        }
                    });
                }
                catch(SecurityException e){
                    Log.e(TAG, "Discover button error : "+e);
                    e.printStackTrace();
                }
            }
        });

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                final WifiP2pDevice device = deviceArray[i];
                WifiP2pConfig config = new WifiP2pConfig();
                config.deviceAddress = device.deviceAddress;
                try{
                    manager.connect(channel, config, new WifiP2pManager.ActionListener() {
                        @Override
                        public void onSuccess() {
                            connectionStatus.setText("Connecting to : "+device.deviceName);
                        }

                        @Override
                        public void onFailure(int i) {
                            connectionStatus.setText("Not connected");
                        }
                    });
                }
                catch(SecurityException e){
                    Log.e(TAG, "List View on click error : "+e);
                    e.printStackTrace();
                }
            }
        });

        sendFileButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                flag = 1;
                Log.e(TAG, "" + (int) file.length());
                Log.e(TAG, "" + file.getName());
                fileNameTextView.setText("Sending File : " + file.getName());

                ExecutorService executorService = Executors.newSingleThreadExecutor();
                executorService.execute(new Runnable() {
                    @Override
                    public void run() {
                        if ((int)file.length() > 0 && isHost) {
                            serverClass.write(file);
                        } else if ((int)file.length() > 0 && !isHost) {
                            clientClass.write(file);
                        }
                    }
                });
            }
        });

        selectFileButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("*/*");
                startActivityForResult(intent, 1);
            }
        });
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if(requestCode == 1 && resultCode == RESULT_OK) {
            Uri fileUri = data.getData();
            String path = fileUri.getPath();

            Log.e(TAG, ""+path);
            File file1 = new File(Environment.getExternalStorageDirectory(), path);

            for(int i=0; i<path.length()-1; i++){
                Log.e(TAG, i+"..."+file1.getPath());
                if((path.charAt(i)<65 || path.charAt(i)>90) && (path.charAt(i)<97 || path.charAt(i)>122)) {
                    file1 = new File(Environment.getExternalStorageDirectory(), path.substring(i + 1));
                    Log.e(TAG, i + "..." + file1.getPath());
                    if ((int) file1.length() > 0) {
                        break;
                    }
                }
            }
            File file2 = file1;
            file = file2;
            int size = (int)file2.length();
            System.out.println(size);
            if(size == 0){
                Toast.makeText(this, "File not found. Please select again from File Manager!", Toast.LENGTH_LONG).show();

                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("*/*");

                startActivityForResult(intent, 1);
            }
            fileNameTextView.setText("Selected File : " + file2.getName());
        }
    }

    public void initialWork(){
        connectionStatus = findViewById(R.id.connection_status);
        dataTextView = findViewById(R.id.dataTextView);
        fileNameTextView = findViewById(R.id.fileTextView);
        discoverButton = findViewById(R.id.buttonDiscover);
        listView = findViewById(R.id.listView);
        selectFileButton = findViewById(R.id.select_file_button);
        sendFileButton = findViewById(R.id.sendButton);

        manager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
        channel = manager.initialize(this, getMainLooper(), null);
        receiver = new WiFiDirectBroadcastReceiver(manager, channel, this);

        intentFilter = new IntentFilter();
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
    }

    WifiP2pManager.PeerListListener peerListListener = new WifiP2pManager.PeerListListener() {
        @Override
        public void onPeersAvailable(WifiP2pDeviceList wifiP2pDeviceList) {
            if(!wifiP2pDeviceList.equals(peers)){
                peers.clear();
                peers.addAll(wifiP2pDeviceList.getDeviceList());
                deviceNameArray = new String[wifiP2pDeviceList.getDeviceList().size()];
                deviceArray = new WifiP2pDevice[wifiP2pDeviceList.getDeviceList().size()];
                int idx = 0;
                for(WifiP2pDevice device:wifiP2pDeviceList.getDeviceList()){
                    deviceNameArray[idx] = device.deviceName+" ("+device.deviceAddress+")";
                    deviceArray[idx] = device;
                    idx++;
                }

                ArrayAdapter<String> adapter = new ArrayAdapter<String>(getApplicationContext(), android.R.layout.simple_list_item_1, deviceNameArray);
                listView.setAdapter(adapter);

                if (peers.size() == 0) {
                    connectionStatus.setText("No device found");
                    return;
                }
            }
        }
    };

    WifiP2pManager.ConnectionInfoListener connectionInfoListener = new WifiP2pManager.ConnectionInfoListener() {
        @Override
        public void onConnectionInfoAvailable(WifiP2pInfo wifiP2pInfo) {
            final InetAddress groupOwnerAddress = wifiP2pInfo.groupOwnerAddress;
            if(wifiP2pInfo.groupFormed && wifiP2pInfo.isGroupOwner){
                connectionStatus.setText("Host");
                isHost = true;
                serverClass = new ServerClass();
                serverClass.start();
            }
            else if(wifiP2pInfo.groupFormed){
                connectionStatus.setText("Client");
                isHost = false;
                clientClass = new ClientClass(groupOwnerAddress);
                clientClass.start();
            }
        }
    };

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(receiver, intentFilter);
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(receiver);
    }

    public class ServerClass extends Thread{
        ServerSocket serverSocket;
        InputStream inputStream;
        OutputStream outputStream;

        public void write(File file) {
            try {
                byte[] buffer = new byte[1024 * 1024];
                InputStream inputStream = new FileInputStream(file);
                OutputStream outputStream = socket.getOutputStream();

                DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
                String fileName = file.getName();
                dos.writeUTF(fileName);
                dos.flush();

                int bytesRead;
                long c=0;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    c+=bytesRead;
                    dataTextView.setText((c*100/file.length())+"% Sent");
                    outputStream.write(buffer, 0, bytesRead);
                }
                inputStream.close();
                outputStream.close();
                dos.close();
                socket.close();

                dataTextView.setText("File Sent");
            }
            catch (IOException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void run() {
            try {
                serverSocket = new ServerSocket(8888);
                socket = serverSocket.accept();
                inputStream = socket.getInputStream();
                outputStream = socket.getOutputStream();

                InputStream in = socket.getInputStream();
                DataInputStream dis = new DataInputStream(socket.getInputStream());

                File downloadsFolder = new File(Environment.getExternalStorageDirectory(), "Download");
                String filename = "/"+dis.readUTF();
                File downloadsFolderFiles[] = downloadsFolder.listFiles();
                int c = 1;
                if(downloadsFolderFiles != null) {
                    for (File d : downloadsFolderFiles) {
                        if(d.isFile() && d.getName().equals(filename)){
                            filename = filename+"("+c+")";
                            c++;
                        }
                    }
                }
                File file1 = new File(downloadsFolder + filename);
                OutputStream out = new FileOutputStream(file1);

                byte[] buffer = new byte[1024 * 1024];
                int bytesRead = 0;
                while ((bytesRead = in.read(buffer)) != -1) {
                    dataTextView.setText("Receiving File...");
                    out.write(buffer, 0, bytesRead);
                }
                dataTextView.setText("File Received");
                fileNameTextView.setText("File location : "+file1.getPath());

                dis.close();
                in.close();
                out.close();
                socket.close();
                serverSocket.close();
            }
            catch (IOException e) {
                e.printStackTrace();
            }

        }
    }

    public class ClientClass extends Thread{
        String hostAdd;
        InputStream inputStream;
        OutputStream outputStream;

        public ClientClass(InetAddress hostAddress){
            hostAdd = hostAddress.getHostAddress();
            socket = new Socket();
        }

        public void write(File file) {
            try {
                byte[] buffer = new byte[1024 * 1024];
                InputStream inputStream = new FileInputStream(file);
                OutputStream outputStream = socket.getOutputStream();

                DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
                String fileName = file.getName();
                dos.writeUTF(fileName);
                dos.flush();

                int bytesRead;
                long c=0;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    c+=bytesRead;
                    Log.d(TAG, "Bytes reaD = "+c);
                    dataTextView.setText((c*100/file.length())+"% Sent");
                    outputStream.write(buffer, 0, bytesRead);
                }
                dos.close();
                inputStream.close();
                outputStream.close();
                socket.close();

                dataTextView.setText("File Sent");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }


        @Override
        public void run() {
            try {
                socket.connect(new InetSocketAddress(hostAdd, 8888), 50000);
                inputStream = socket.getInputStream();
                outputStream = socket.getOutputStream();

                InputStream in = socket.getInputStream();
                DataInputStream dis = new DataInputStream(socket.getInputStream());

                File downloadsFolder = new File(Environment.getExternalStorageDirectory(), "Download");
                String filename = "/"+dis.readUTF();
                File downloadsFolderFiles[] = downloadsFolder.listFiles();
                int c = 1;
                if(downloadsFolderFiles != null) {
                    for (File d : downloadsFolderFiles) {
                        if(d.isFile() && d.getName().equals(filename)){
                            filename = filename+"("+c+")";
                            c++;
                        }
                    }
                }
                File file1 = new File(downloadsFolder + filename);
                OutputStream out = new FileOutputStream(file1);

                byte[] buffer = new byte[1024 * 1024];
                int bytesRead = 0;
                while ((bytesRead = in.read(buffer)) != -1) {
                    dataTextView.setText("Receiving File...");
                    out.write(buffer, 0, bytesRead);
                }
                dataTextView.setText("File Received");
                fileNameTextView.setText("File location : "+file1.getPath());

                dis.close();
                in.close();
                out.close();
                socket.close();
            }
            catch(IOException e) {
                e.printStackTrace();
            }
        }
    }
}
