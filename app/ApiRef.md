
`ConnectingActivity.kt`: Start BluetoothSpeckService
`BluetoothSpeckService.java`: 

```Kotlin
connectSensorsButton.setOnClickListener {
    sharedPreferences.edit().putString(
        Constants.RESPECK_MAC_ADDRESS_PREF,
        respeckID.text.toString()
    ).apply()
    sharedPreferences.edit().putInt(Constants.RESPECK_VERSION, 6).apply()

    sharedPreferences.edit().putString(
        Constants.THINGY_MAC_ADDRESS_PREF,
        thingyID.text.toString()
    ).apply()

    startSpeckService()

}
```

```Kotlin
fun startSpeckService() {
    val isServiceRunning =
        Utils.isServiceRunning(BluetoothSpeckService::class.java, applicationContext)
    Log.i("service", "isServiceRunning = $isServiceRunning")

    if (!isServiceRunning) {
        Log.i("service", "Starting BLT service")
        val simpleIntent = Intent(this, BluetoothSpeckService::class.java)
        this.startService(simpleIntent)
    } else {
        Log.i("service", "Service already running, restart")
        this.stopService(Intent(this, BluetoothSpeckService::class.java))
        Toast.makeText(this, "restarting service with new sensors", Toast.LENGTH_SHORT).show()
        this.startService(Intent(this, BluetoothSpeckService::class.java))
    }
}
```