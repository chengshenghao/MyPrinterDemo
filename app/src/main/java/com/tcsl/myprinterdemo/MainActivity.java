package com.tcsl.myprinterdemo;

import android.Manifest;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.support.annotation.IdRes;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.rt.printerlibrary.bean.BluetoothEdrConfigBean;
import com.rt.printerlibrary.bean.UsbConfigBean;
import com.rt.printerlibrary.bean.WiFiConfigBean;
import com.rt.printerlibrary.cmd.Cmd;
import com.rt.printerlibrary.cmd.EscFactory;
import com.rt.printerlibrary.connect.PrinterInterface;
import com.rt.printerlibrary.enumerate.CommonEnum;
import com.rt.printerlibrary.enumerate.ConnectStateEnum;
import com.rt.printerlibrary.factory.cmd.CmdFactory;
import com.rt.printerlibrary.factory.connect.PIFactory;
import com.rt.printerlibrary.factory.connect.UsbFactory;
import com.rt.printerlibrary.factory.printer.LabelPrinterFactory;
import com.rt.printerlibrary.factory.printer.PinPrinterFactory;
import com.rt.printerlibrary.factory.printer.PrinterFactory;
import com.rt.printerlibrary.factory.printer.ThermalPrinterFactory;
import com.rt.printerlibrary.factory.printer.UniversalPrinterFactory;
import com.rt.printerlibrary.observer.PrinterObserver;
import com.rt.printerlibrary.observer.PrinterObserverManager;
import com.rt.printerlibrary.printer.RTPrinter;
import com.tcsl.myprinterdemo.Base.BaseActivity;
import com.tcsl.myprinterdemo.Base.BaseApplication;
import com.tcsl.myprinterdemo.dialog.UsbDeviceChooseDialog;
import com.tcsl.myprinterdemo.utils.BaseEnum;
import com.tcsl.myprinterdemo.utils.TimeRecordUtils;
import com.tcsl.myprinterdemo.view.FlowRadioGroup;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends BaseActivity implements View.OnClickListener, PrinterObserver {
    //权限申请
    private String[] NEED_PERMISSION = {
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };
    private List<String> NO_PERMISSION = new ArrayList<String>();
    private static final int REQUEST_CAMERA = 0;
    private RTPrinter rtPrinter = null;
    private PrinterFactory printerFactory;
    private ProgressBar pb_connect;
    private TextView tv_device_selected;
    private Button btn_disConnect, btn_connect, btn_selftest_print;
    private PrinterInterface curPrinterInterface = null;
    @BaseEnum.ConnectType
    private int checkedConType = BaseEnum.CON_USB;
    private Object configObj;
    private ArrayList<PrinterInterface> printerInterfaceArrayList = new ArrayList<>();


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        CheckAllPermission();
        initView();
        init();
        addListener();
    }

    public void initView() {
        tv_device_selected = findViewById(R.id.tv_device_selected);
        pb_connect = findViewById(R.id.pb_connect);
        btn_connect = findViewById(R.id.btn_connect);
        btn_selftest_print = findViewById(R.id.btn_selftest_print);
    }

    /**
     * 初始化打印相关信息
     */
    public void init() {
        //初始化为针打printer
//        BaseApplication.instance.setCurrentCmdType(BaseEnum.CMD_ESC);
//        printerFactory = new UniversalPrinterFactory();
//        rtPrinter = printerFactory.create();
        PrinterObserverManager.getInstance().add(this);//添加连接状态监听
        //默认指令类型为Esc
        BaseApplication.instance.setCurrentCmdType(BaseEnum.CMD_ESC);
        printerFactory = new ThermalPrinterFactory();
        rtPrinter = printerFactory.create();
        rtPrinter.setPrinterInterface(curPrinterInterface);
    }

    /**
     * 添加监听
     */
    public void addListener() {
        tv_device_selected.setOnClickListener(this);
        btn_selftest_print.setOnClickListener(this);
        btn_connect.setOnClickListener(this);
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.tv_device_selected://未连接打印机选择
                showUSBDeviceChooseDialog();
                break;
            case R.id.btn_connect:
                doConnect();
                break;
            case R.id.btn_selftest_print://测试页打印
                escSelftestPrint();
                break;
            default:
                break;
        }
    }

    private void escSelftestPrint() {
        CmdFactory cmdFactory = new EscFactory();
        Cmd cmd = cmdFactory.create();
        cmd.append(cmd.getHeaderCmd());
        cmd.append(cmd.getLFCRCmd());
        cmd.append(cmd.getSelfTestCmd());
        cmd.append(cmd.getLFCRCmd());
        rtPrinter.writeMsgAsync(cmd.getAppendCmds());
    }
    /**
     * usb设备选择
     */
    private void showUSBDeviceChooseDialog() {
        final UsbDeviceChooseDialog usbDeviceChooseDialog = new UsbDeviceChooseDialog();
        usbDeviceChooseDialog.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                UsbDevice mUsbDevice = (UsbDevice) parent.getAdapter().getItem(position);
                PendingIntent mPermissionIntent = PendingIntent.getBroadcast(
                        MainActivity.this,
                        0,
                        new Intent(MainActivity.this.getApplicationInfo().packageName),
                        0);
                tv_device_selected.setText(getString(R.string.adapter_usbdevice) + mUsbDevice.getDeviceId()); //+ (position + 1));
                configObj = new UsbConfigBean(BaseApplication.getInstance(), mUsbDevice, mPermissionIntent);
                tv_device_selected.setTag(BaseEnum.HAS_DEVICE);
//                isConfigPrintEnable(configObj);
                usbDeviceChooseDialog.dismiss();
            }
        });
        usbDeviceChooseDialog.show(getFragmentManager(), null);
    }

    /**
     * 连接USB设备
     */
    private void doConnect() {
        if (Integer.parseInt(tv_device_selected.getTag().toString()) == BaseEnum.NO_DEVICE) {//未选择设备
            showAlertDialog(getString(R.string.main_pls_choose_device));
            return;
        }
        pb_connect.setVisibility(View.VISIBLE);
        UsbConfigBean usbConfigBean = (UsbConfigBean) configObj;
        connectUSB(usbConfigBean);
    }

    private void connectUSB(UsbConfigBean usbConfigBean) {
        UsbManager mUsbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        PIFactory piFactory = new UsbFactory();
        PrinterInterface printerInterface = piFactory.create();
        printerInterface.setConfigObject(usbConfigBean);
        rtPrinter.setPrinterInterface(printerInterface);

        if (mUsbManager.hasPermission(usbConfigBean.usbDevice)) {
            try {
                rtPrinter.connect(usbConfigBean);
                BaseApplication.instance.setRtPrinter(rtPrinter);
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            mUsbManager.requestPermission(usbConfigBean.usbDevice, usbConfigBean.pendingIntent);
        }

    }

    /**
     * 断开连接
     */
    private void doDisConnect() {
        if (Integer.parseInt(tv_device_selected.getTag().toString()) == BaseEnum.NO_DEVICE) {//未选择设备
//            showAlertDialog(getString(R.string.main_discon_click_repeatedly));
            return;
        }

        if (rtPrinter != null && rtPrinter.getPrinterInterface() != null) {
            rtPrinter.disConnect();
        }
        tv_device_selected.setText(getString(R.string.please_connect));
        tv_device_selected.setTag(BaseEnum.NO_DEVICE);
    }

    @Override
    public void printerObserverCallback(final PrinterInterface printerInterface, final int state) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                pb_connect.setVisibility(View.GONE);
                switch (state) {
                    case CommonEnum.CONNECT_STATE_SUCCESS:
                        TimeRecordUtils.record("RT连接end：", System.currentTimeMillis());
                        Toast.makeText(MainActivity.this, printerInterface.getConfigObject().toString() + getString(R.string._main_connected), Toast.LENGTH_SHORT).show();
                        tv_device_selected.setText(printerInterface.getConfigObject().toString());
                        tv_device_selected.setTag(BaseEnum.HAS_DEVICE);
                        curPrinterInterface = printerInterface;//设置为当前连接， set current Printer Interface
                        printerInterfaceArrayList.add(printerInterface);//多连接-添加到已连接列表
                        rtPrinter.setPrinterInterface(printerInterface);
                        BaseApplication.getInstance().setRtPrinter(rtPrinter);
                        break;
                    case CommonEnum.CONNECT_STATE_INTERRUPTED:
                        if (printerInterface != null && printerInterface.getConfigObject() != null) {
                            Toast.makeText(MainActivity.this, printerInterface.getConfigObject().toString() + getString(R.string._main_disconnect), Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(MainActivity.this, getString(R.string._main_disconnect), Toast.LENGTH_SHORT).show();

                        }
                        tv_device_selected.setText(R.string.please_connect);
                        tv_device_selected.setTag(BaseEnum.NO_DEVICE);
                        curPrinterInterface = null;
                        printerInterfaceArrayList.remove(printerInterface);//多连接-从已连接列表中移除
                        BaseApplication.getInstance().setRtPrinter(null);
                        break;
                    default:
                        break;
                }
            }
        });
    }

    @Override
    public void printerReadMsgCallback(PrinterInterface printerInterface, byte[] bytes) {

    }

    /**
     * 校验权限
     */
    private void CheckAllPermission() {
        NO_PERMISSION.clear();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            for (int i = 0; i < NEED_PERMISSION.length; i++) {
                if (checkSelfPermission(NEED_PERMISSION[i]) != PackageManager.PERMISSION_GRANTED) {
                    NO_PERMISSION.add(NEED_PERMISSION[i]);
                }
            }
            if (NO_PERMISSION.size() == 0) {
                recordVideo();
            } else {
                requestPermissions(NO_PERMISSION.toArray(new String[NO_PERMISSION.size()]), REQUEST_CAMERA);
            }
        } else {
            recordVideo();
        }
    }

    private void recordVideo() {
        Log.d("MainActivity", "有权限了");
    }

}
