package de.simon.dankelmann.bluetoothlespam.Handlers

import android.os.Handler
import android.os.Looper
import android.util.Log
import de.simon.dankelmann.bluetoothlespam.AppContext.AppContext
import de.simon.dankelmann.bluetoothlespam.Enums.AdvertisementError
import de.simon.dankelmann.bluetoothlespam.Enums.AdvertisementQueueMode
import de.simon.dankelmann.bluetoothlespam.Enums.TxPowerLevel
import de.simon.dankelmann.bluetoothlespam.Helpers.QueueHandlerHelpers
import de.simon.dankelmann.bluetoothlespam.Interfaces.Callbacks.IAdvertisementServiceCallback
import de.simon.dankelmann.bluetoothlespam.Interfaces.Services.IAdvertisementService
import de.simon.dankelmann.bluetoothlespam.Models.AdvertisementSet
import de.simon.dankelmann.bluetoothlespam.Models.AdvertisementSetCollection
import de.simon.dankelmann.bluetoothlespam.Models.AdvertisementSetList
import kotlin.random.Random

class  AdvertisementSetQueueHandler :IAdvertisementServiceCallback {

    // private
    private var _logTag = "AdvertisementSetQueuHandler"
    private var _advertisementService:IAdvertisementService? = null
    private var _advertisementSetCollection:AdvertisementSetCollection = AdvertisementSetCollection()
    private var _interval:Long = 1000
    private var _advertisementServiceCallbacks:MutableList<IAdvertisementServiceCallback> = mutableListOf()
    private var _active = false
    private var _advertisementQueueMode: AdvertisementQueueMode = AdvertisementQueueMode.ADVERTISEMENT_QUEUE_MODE_LINEAR

    private var _currentAdvertisementSet: AdvertisementSet? = null
    private var _currentAdvertisementSetListIndex = 0
    private var _currentAdvertisementSetIndex = 0

    init{
        _advertisementService = AppContext.getAdvertisementService()
        if(_advertisementService != null){
            _advertisementService!!.addAdvertisementServiceCallback(this)
        }

        setInterval(QueueHandlerHelpers.getInterval())
    }

    fun setAdvertisementQueueMode(advertisementQueueMode: AdvertisementQueueMode){
        _advertisementQueueMode = advertisementQueueMode
    }

    fun getAdvertisementQueueMode():AdvertisementQueueMode{
        return _advertisementQueueMode
    }

    fun setAdvertisementService(advertisementService: IAdvertisementService){
        _advertisementService = advertisementService
        _advertisementService!!.addAdvertisementServiceCallback(this)
    }

    fun setTxPowerLevel(txPowerLevel: TxPowerLevel){
        if(_advertisementService != null){
            _advertisementService!!.setTxPowerLevel(txPowerLevel)
        }
    }

    fun setSelectedAdvertisementSet(advertisementSetListIndex: Int, advertisementSetIndex: Int){
        if(_advertisementSetCollection.advertisementSetLists[advertisementSetListIndex] != null){
            if(_advertisementSetCollection.advertisementSetLists[advertisementSetListIndex].advertisementSets[advertisementSetIndex] != null){
                _currentAdvertisementSetListIndex = advertisementSetListIndex
                _currentAdvertisementSetIndex = advertisementSetIndex
                _currentAdvertisementSet = _advertisementSetCollection.advertisementSetLists[advertisementSetListIndex].advertisementSets[advertisementSetIndex]
            }
        }
    }

    fun setAdvertisementSetCollection(advertisementSetCollection: AdvertisementSetCollection){
        _advertisementSetCollection = advertisementSetCollection

        // Reset indices
        _currentAdvertisementSet= null
        _currentAdvertisementSetListIndex = 0
        _currentAdvertisementSetIndex = 0
    }

    fun getAdvertisementSetCollection(): AdvertisementSetCollection{
        return _advertisementSetCollection
    }

    // Add / Remove AdvertisementSetCollections
    fun clearAdvertisementSetCollection(){
        _advertisementSetCollection.advertisementSetLists.clear()
    }
    fun addAdvertisementSetList(advertisementSetList: AdvertisementSetList){
        if(!_advertisementSetCollection.advertisementSetLists.contains(advertisementSetList)){
            _advertisementSetCollection.advertisementSetLists.add(advertisementSetList)
        }
    }

    fun removeAdvertisementSetList(advertisementSetList: AdvertisementSetList){
        if(_advertisementSetCollection.advertisementSetLists.contains(advertisementSetList)){
            _advertisementSetCollection.advertisementSetLists.remove(advertisementSetList)
        }
    }

    // Add / Remove Callbacks
    fun addAdvertisementServiceCallback(callback: IAdvertisementServiceCallback){
        if(!_advertisementServiceCallbacks.contains(callback)){
            _advertisementServiceCallbacks.add(callback)
        }
    }
    fun removeAdvertisementServiceCallback(callback: IAdvertisementServiceCallback){
        if(_advertisementServiceCallbacks.contains(callback)){
            _advertisementServiceCallbacks.remove(callback)
        }
    }

    fun setIntervalSeconds(seconds:Int){
        _interval = (seconds * 1000).toLong()
    }

    fun setInterval(milliseconds:Int){
        if(milliseconds > 0){
            _interval = milliseconds.toLong()
        }
    }

    fun activate(){
        _active = true
        if(_currentAdvertisementSet != null){
            handleAdvertisementSet(_currentAdvertisementSet!!)
        } else {
            advertiseNextAdvertisementSet()
        }
    }

    fun deactivate(){
        _active = false
        if(_advertisementService != null){
            _advertisementService!!.stopAdvertisement()
        }
    }

    fun advertiseNextAdvertisementSet(){
        selectNextAdvertisementSet()
        if(_currentAdvertisementSet != null){
            handleAdvertisementSet(_currentAdvertisementSet!!)
        } else {
            Log.e(_logTag, "Current Advertisement Set is null.")
        }
    }

    fun selectNextAdvertisementSet(){
        var nextAdvertisementSet: AdvertisementSet? = _currentAdvertisementSet
        var nextAdvertisementSetListIndex = _currentAdvertisementSetListIndex
        var nextAdvertisementSetIndex = _currentAdvertisementSetIndex

        when(_advertisementQueueMode){
            AdvertisementQueueMode.ADVERTISEMENT_QUEUE_MODE_SINGLE -> {
                // If no AdvertisementSet is selected, select the first set in the first list
                if(_currentAdvertisementSet == null){
                    if(_advertisementSetCollection.advertisementSetLists.isNotEmpty()){
                        val firstList = _advertisementSetCollection.advertisementSetLists.first()
                        if(firstList.advertisementSets.isNotEmpty()){
                            nextAdvertisementSetListIndex = 0
                            nextAdvertisementSetIndex = 0
                            nextAdvertisementSet = firstList.advertisementSets.first()
                        }
                    }
                }
            }

            AdvertisementQueueMode.ADVERTISEMENT_QUEUE_MODE_LINEAR -> {
                // If no AdvertisementSet is selected, select the first set in the first list
                if(_currentAdvertisementSet == null){
                    if(_advertisementSetCollection.advertisementSetLists.isNotEmpty()){
                        val firstList = _advertisementSetCollection.advertisementSetLists.first()
                        if(firstList.advertisementSets.isNotEmpty()){
                            nextAdvertisementSetListIndex = 0
                            nextAdvertisementSetIndex = 0
                            nextAdvertisementSet = firstList.advertisementSets.first()
                        }
                    }
                } else {
                    var selectedList = _advertisementSetCollection.advertisementSetLists[_currentAdvertisementSetListIndex]
                    Log.d(_logTag, "List: ${selectedList.title}, SETS: ${selectedList.advertisementSets.count()}, CurrentIndex: ${_currentAdvertisementSetIndex}")
                    if(_currentAdvertisementSetIndex >= (selectedList.advertisementSets.count() - 1)){
                        // SET ADVERTISEMENT SET INDEX TO 0
                        nextAdvertisementSetIndex = 0

                        // SELECT NEXT LIST
                        if(_currentAdvertisementSetListIndex >= (_advertisementSetCollection.advertisementSetLists.count() - 1)){
                            nextAdvertisementSetListIndex = 0
                        } else {
                            nextAdvertisementSetListIndex++
                        }

                        selectedList = _advertisementSetCollection.advertisementSetLists[nextAdvertisementSetListIndex]

                        // SET THE ITEM
                        nextAdvertisementSet = selectedList.advertisementSets[nextAdvertisementSetIndex]
                    } else {
                        nextAdvertisementSetIndex++
                        nextAdvertisementSet = selectedList.advertisementSets[nextAdvertisementSetIndex]
                    }

                }
            }

            AdvertisementQueueMode.ADVERTISEMENT_QUEUE_MODE_LIST -> {
                // If no AdvertisementSet is selected, select the first set in the first list
                if(_currentAdvertisementSet == null){
                    if(_advertisementSetCollection.advertisementSetLists.isNotEmpty()){
                        val firstList = _advertisementSetCollection.advertisementSetLists.first()
                        if(firstList.advertisementSets.isNotEmpty()){
                            nextAdvertisementSetListIndex = 0
                            nextAdvertisementSetIndex = 0
                            nextAdvertisementSet = firstList.advertisementSets.first()
                        }
                    }
                } else {
                    var selectedList = _advertisementSetCollection.advertisementSetLists[_currentAdvertisementSetListIndex]
                    Log.d(_logTag, "List: ${selectedList.title}, SETS: ${selectedList.advertisementSets.count()}, CurrentIndex: ${_currentAdvertisementSetIndex}")
                    if(_currentAdvertisementSetIndex >= (selectedList.advertisementSets.count() - 1)){
                        // SET ADVERTISEMENT SET INDEX TO 0
                        nextAdvertisementSetIndex = 0

                        selectedList = _advertisementSetCollection.advertisementSetLists[nextAdvertisementSetListIndex]

                        // SET THE ITEM
                        nextAdvertisementSetListIndex = _currentAdvertisementSetListIndex
                        nextAdvertisementSet = selectedList.advertisementSets[nextAdvertisementSetIndex]
                    } else {
                        nextAdvertisementSetIndex++
                        nextAdvertisementSet = selectedList.advertisementSets[nextAdvertisementSetIndex]
                    }

                }
            }

            AdvertisementQueueMode.ADVERTISEMENT_QUEUE_MODE_RANDOM -> {
                nextAdvertisementSetListIndex = Random.nextInt(_advertisementSetCollection.advertisementSetLists.size);
                val nextAdvertisementSetList = _advertisementSetCollection.advertisementSetLists.get(nextAdvertisementSetListIndex)
                nextAdvertisementSetIndex = Random.nextInt(nextAdvertisementSetList.advertisementSets.size)
                nextAdvertisementSet = nextAdvertisementSetList.advertisementSets[nextAdvertisementSetIndex]
            }
        }

        _currentAdvertisementSet = nextAdvertisementSet
        _currentAdvertisementSetListIndex = nextAdvertisementSetListIndex
        _currentAdvertisementSetIndex = nextAdvertisementSetIndex
    }

    private fun handleAdvertisementSet(advertisementSet: AdvertisementSet){
        if(_active && _advertisementService != null){
            _advertisementService!!.startAdvertisement(advertisementSet)
        }
    }

    fun isActive():Boolean{
        return _active
    }

    fun onAdvertisementSucceeded(){
        if(_advertisementService != null){
            _advertisementService!!.stopAdvertisement()

            if(_advertisementService!!.isLegacyService()){
                advertiseNextAdvertisementSet()
            } else {
                // Wait for the Stop Advertising Callback
            }
        }
    }

    fun onAdvertisementFailed(){
        Log.d(_logTag, "Advertisement failed, trying again")
        onAdvertisementSucceeded()
    }

    private fun runLocalCallback(success:Boolean){
        Handler(Looper.getMainLooper()).postDelayed(object : Runnable {
            override fun run() {
                if(success){
                    onAdvertisementSucceeded()
                } else {
                    onAdvertisementFailed()
                }
            }
        }, _interval)
    }

    // Callback Implementation, just pass to own Listeners
    override fun onAdvertisementSetStart(advertisementSet: AdvertisementSet?) {
        _advertisementServiceCallbacks.map {
            it.onAdvertisementSetStart(advertisementSet)
        }
    }

    override fun onAdvertisementSetStop(advertisementSet: AdvertisementSet?) {
        _advertisementServiceCallbacks.map {
            it.onAdvertisementSetStop(advertisementSet)
        }

        if(_advertisementService != null && !_advertisementService!!.isLegacyService()){
            advertiseNextAdvertisementSet()
        }
    }

    override fun onAdvertisementSetSucceeded(advertisementSet: AdvertisementSet?) {
        runLocalCallback(true)
        _advertisementServiceCallbacks.map {
            it.onAdvertisementSetSucceeded(advertisementSet)
        }
    }

    override fun onAdvertisementSetFailed(advertisementSet: AdvertisementSet?, advertisementError: AdvertisementError) {
        runLocalCallback(false)
        _advertisementServiceCallbacks.map {
            it.onAdvertisementSetFailed(advertisementSet, advertisementError)
        }
    }
}