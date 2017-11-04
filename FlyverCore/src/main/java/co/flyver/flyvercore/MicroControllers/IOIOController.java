package co.flyver.flyvercore.MicroControllers;


import co.flyver.flyvercore.DroneTypes.QuadCopterX;
import co.flyver.flyvercore.MainControllers.MainController;
import ioio.lib.api.AnalogInput;
import ioio.lib.api.PwmOutput;
import ioio.lib.api.exception.ConnectionLostException;
import ioio.lib.util.BaseIOIOLooper;

/**
 * IOIOController is used to setup and loop the IOIO OTG Board.
 * In here all inputs and outputs of the board shall be implemented.
 * This IOIOController is used only with QuadCopterX of Drone type.
 * A new IOIOController or MicroController shall be made for each new Drone type.
 */
public class IOIOController extends BaseIOIOLooper implements MicroController {

    public interface ConnectionHooks {
        /**
         * This method will be called every time the board connects and reconnects
         * @param ioioController
         */
        public void onConnect(IOIOController ioioController);
        public void onDisconnect();
    }

    private PwmOutput motorFC; // Front clockwise motor
    private PwmOutput motorFCC; // Front counterclockwise motor
    private PwmOutput motorRC; // Rear clockwise motor
    private PwmOutput motorRCC; // Rear counterclockwise motor
    private AnalogInput batteryInput;
    QuadCopterX quadCopterX;
    private float[] motorPowers;
    private float batteryVoltage;
    private MainController mainController;
    public boolean ioioref = false;
    private ConnectionHooks connectionHooks;

    public IOIOController addConnectionHooks(ConnectionHooks hooks) {
        this.connectionHooks = hooks;
        return this;
    }

    @Override
    public float getBatteryVoltage() {
        return batteryVoltage;
    }

    public IOIOController(QuadCopterX quadCopterX){
        this.quadCopterX = quadCopterX;
    }
    // TODO: Battery voltage onChange intent


    @Override
    public void setup() throws ConnectionLostException {
        ioioref = true;
        int mFrequncy = 200;
        motorFC = ioio_.openPwmOutput(34, mFrequncy);
        motorFCC = ioio_.openPwmOutput(35, mFrequncy);
        motorRC = ioio_.openPwmOutput(36, mFrequncy);
        motorRCC = ioio_.openPwmOutput(37, mFrequncy);
        batteryInput = ioio_.openAnalogInput(46);
        if(connectionHooks != null) {
            connectionHooks.onConnect(this);
        }
    }

    /**
     * Called repetitively while the IOIO is connected.
     *
     * @throws ConnectionLostException When IOIO connection is lost.
     * @throws InterruptedException    When the IOIO thread has been interrupted.
     * @see ioio.lib.util.IOIOLooper#loop()
     */
    @Override
    public void loop() throws InterruptedException, ConnectionLostException {
        try {

            motorPowers = quadCopterX.getMotorPowers();
            motorFC.setPulseWidth(motorPowers[0] + 1000);
            motorFCC.setPulseWidth(motorPowers[1] + 1000);
            motorRC.setPulseWidth(motorPowers[2] + 1000);
            motorRCC.setPulseWidth(motorPowers[3] + 1000);
            batteryVoltage = batteryInput.getVoltage();
//            Log.d("IOIOC", "Battery voltage: " + batteryVoltage);
            // Log.i("motors", debugText);

            // stateView.setText(motorsStateString);
            Thread.sleep(1);
        } catch (InterruptedException e) {
            ioio_.disconnect();
        } catch (ConnectionLostException e) {
            throw e;
        }
    }

    /**
     * Called when the IOIO is disconnected.
     *
     * @see ioio.lib.util.IOIOLooper#disconnected()
     */
    @Override
    public void disconnected() {
        if(connectionHooks != null) {
            connectionHooks.onDisconnect();
        }
    }

    /**
     * Called when the IOIO is connected, but has an incompatible firmware version.
     *
     * @see ioio.lib.util.IOIOLooper#incompatible(ioio.lib.api.IOIO)
     */
    @Override
    public void incompatible() {

    }
}