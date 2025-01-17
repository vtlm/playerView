package com.example.playerview

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.text.Layout
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.documentfile.provider.DocumentFile
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackGroup
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.MetadataRetriever
import androidx.media3.exoplayer.source.TrackGroupArray
import androidx.media3.extractor.metadata.id3.TextInformationFrame
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.Futures

const val REQ_CODE=0xff0033

class CustomAdapter(private val dataSet: ArrayList<String>,
                    private val onItemClicked: (String) -> Unit) :
    RecyclerView.Adapter<CustomAdapter.ViewHolder>() {

    /**
     * Provide a reference to the type of views that you are using
     * (custom ViewHolder)
     */
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val textView: TextView

        init {
            // Define click listener for the ViewHolder's View
            textView = view.findViewById(R.id.textView)
        }
    }

    // Create new views (invoked by the layout manager)
    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolder {
        // Create a new view, which defines the UI of the list item
        val view = LayoutInflater.from(viewGroup.context)
            .inflate(R.layout.text_row_item, viewGroup, false)

        return ViewHolder(view)
    }

    // Replace the contents of a view (invoked by the layout manager)
    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {

        // Get element from your dataset at this position and replace the
        // contents of the view with that element
        viewHolder.textView.text = dataSet[position]
        val item = dataSet[position]
//        viewHolder.bind(item)
        viewHolder.itemView.setOnClickListener { onItemClicked(item) }

    }

    // Return the size of your dataset (invoked by the layout manager)
    override fun getItemCount() = dataSet.size

}

@UnstableApi
class MediaData(val uri: Uri, private val context: Context, private val onDataReady: () -> Unit){

    private val mediaItem = MediaItem.Builder().setUri(uri).build();
    private val trackGroupsFuture = MetadataRetriever.retrieveMetadata(context, mediaItem)
    var trackGroup: TrackGroup? = null
    var requestDone: Int = 0
    lateinit var titleString:String

    fun requestMediaData() {
        Futures.addCallback(
            trackGroupsFuture,
            object : FutureCallback<TrackGroupArray?> {
                override fun onSuccess(trackGroups: TrackGroupArray?) {
                    Log.d("DBG_H","mediaItem: $mediaItem.toString()")
                    if (trackGroups != null && (trackGroups.length > 0)) {//handleMetadata(trackGroups)
                        //trackGroups.length
                        trackGroup = trackGroups[0]
                        val format = trackGroup!!.getFormat(0)
                        val metadata = format.metadata

                        var resStr:String = ""

                        for(i in 0 .. metadata!!.length() - 1){
//                            Log.d("DBG_M", metadata.get(i).toString())
                            val ti: TextInformationFrame? = metadata.get(i) as? TextInformationFrame
                            Log.d("DBG_MD","id: ${ti?.id} ${ti?.values?.get(0)}")
                            if(ti?.id == "TPE1"){
                                resStr += ti?.values?.get(0).toString()
                                resStr += " - "
                            }
                            if(ti?.id == "TYER"){
                                resStr += ti?.values?.get(0).toString()
                                resStr += " - "
                            }
                            if(ti?.id == "TALB"){
                                resStr += ti?.values?.get(0).toString()
                                resStr += " - "
                            }
                            if(ti?.id == "TIT2"){
                                resStr += ti?.values?.get(0).toString()
                            }
                        }
                        titleString = resStr
                    }
                    requestDone = 1
                    onDataReady()
                }

                override fun onFailure(t: Throwable) {
                    //handleFailure(t)
                    Log.d("DBG_HF","Failed to retrieve mediadata")
                }
            },
            context.mainExecutor
        )
    }

    fun asString() :String = titleString

}
//todo: move adapter to level up to TrackList
//todo dataset to trackdata
class TrackList(private val context: Context, private val player: Player){

    private var tracksList = arrayListOf<MediaData>()
    private var tracksListAdded=arrayListOf<MediaData>()
    private var dataset = arrayListOf<String>()
    private var mediaSources = arrayListOf<MediaItem>()

    val adapter = CustomAdapter(dataset, onItemClicked = {
        Log.d("DBG_IC","You click $it")
        val ind=dataset.indexOf(it)
        player!!.seekTo(ind,0)
    })

    fun clear(){
        tracksList.clear()
        mediaSources.clear()
        dataset.clear()
        adapter.notifyDataSetChanged()
    }

    fun play(){
//        mediaSources.clear()
//        for(t in tracksList){
//            mediaSources += MediaItem.Builder().setUri(t.uri).build()
//        }
//        player.setMediaItems(mediaSources)
        player.prepare()
        player.play()

    }
    @OptIn(UnstableApi::class)
    fun addFromFiles(files:Array<DocumentFile>){
        tracksListAdded.clear()
//        dataset.clear()

        for(file in files){
            if(file.uri.toString().contains("mp3")){
                tracksListAdded += MediaData(file.uri, context, onDataReady = ::onFilesMediaDataReady)
            }
        }

        for(t in tracksListAdded){
            t.requestMediaData()
        }

    }

    @OptIn(UnstableApi::class)
    fun onFilesMediaDataReady(){
        if(!(tracksListAdded.find {it.requestDone == 0} != null)){

            tracksListAdded.forEach { it-> dataset += it.asString() }
            adapter.notifyDataSetChanged()
            tracksList.addAll(tracksListAdded)
            for(t in tracksListAdded){
                player.addMediaItem(MediaItem.Builder().setUri(t.uri).build())
            }

//            play()
        }

    }
}

class MainActivity : AppCompatActivity() {

    private var trackList: TrackList? = null
    private var exoPlayer: ExoPlayer? = null

    fun onItemClicked(s:String){
        Log.d("DBG_IC","You click $s")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val addButton = findViewById<Button>(R.id.button_choose)
        addButton.setOnClickListener {
            // Code here executes on main thread after user presses button
            Log.d("DBG","button clicked")
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                // Optionally, specify a URI for the directory that should be opened in
                // the system file picker when it loads.
                putExtra(DocumentsContract.EXTRA_INITIAL_URI, "")
            }

            startActivityForResult(intent, REQ_CODE)
        }

        val clearButton = findViewById<Button>(R.id.button_clear)
        clearButton.setOnClickListener {
            exoPlayer?.clearMediaItems()
            trackList?.clear()
        }

        if(exoPlayer == null) {
            exoPlayer = ExoPlayer.Builder(applicationContext).build()
            val playerView = findViewById<PlayerView>(R.id.player_view)
            playerView.player = exoPlayer

            exoPlayer!!.addListener(
                object : Player.Listener {

                    override fun onPlayerError(error: PlaybackException) {
                        val cause = error.cause
                        if (cause is HttpDataSource.HttpDataSourceException) {
                            // An HTTP error occurred.
                            val httpError = cause
                            // It's possible to find out more about the error both by casting and by querying
                            // the cause.
                            if (httpError is HttpDataSource.InvalidResponseCodeException) {
                                // Cast to InvalidResponseCodeException and retrieve the response code, message
                                // and headers.
                            } else {
                                // Try calling httpError.getCause() to retrieve the underlying cause, although
                                // note that it may be null.
                            }
                        }
                    }

                    override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                        var songInfo = "";
                        mediaMetadata.artist?.let {
                            Log.d("DBG", it.toString())
                            songInfo += it.toString()
                        }
                        mediaMetadata.albumTitle?.let {
                            Log.d("DBG", it.toString())
                            songInfo += "-"
                            songInfo += it.toString()
                        }
                        mediaMetadata.title?.let {
                            Log.d("DBG", it.toString())
                            songInfo += "-"
                            songInfo += it.toString()
                        }
                        findViewById<TextView>(R.id.id_title).setText(songInfo)
                    }

                }
            )
        }

        if(trackList == null) {
            trackList = TrackList(applicationContext, exoPlayer!!)
        }

        val recyclerView: RecyclerView = findViewById(R.id.recycler_view)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = trackList!!.adapter


    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        findViewById<ConstraintLayout>(R.id.main).invalidate();

        // Checks whether a keyboard is available
        if (newConfig.keyboardHidden === Configuration.KEYBOARDHIDDEN_YES) {
            Toast.makeText(this, "Keyboard available", Toast.LENGTH_SHORT).show()
        } else if (newConfig.keyboardHidden === Configuration.KEYBOARDHIDDEN_NO) {
            Toast.makeText(this, "No keyboard", Toast.LENGTH_SHORT).show()
        }
    }


    override fun onDestroy() {
        Log.d("DBG_RC","onDestroy")
        super.onDestroy()
        exoPlayer!!.release()
    }

    @OptIn(UnstableApi::class)
    override fun onActivityResult(
        requestCode: Int, resultCode: Int, resultData: Intent?) {
        super.onActivityResult(requestCode, resultCode, resultData)
        if (requestCode == REQ_CODE
            && resultCode == Activity.RESULT_OK) {
            // The result data contains a URI for the document or directory that
            // the user selected.
            resultData?.data?.also { directoryUri ->
                // Perform operations on the document using its URI.
                Log.d("DBG",directoryUri.toString())
                val documentsTree = DocumentFile.fromTreeUri(getApplication(), directoryUri) ?: return
                trackList!!.addFromFiles(documentsTree.listFiles())
            }
        }
    }

}