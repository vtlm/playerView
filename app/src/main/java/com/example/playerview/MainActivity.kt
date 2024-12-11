package com.example.playerview

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.AudioMetadata
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
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
class TrackData(private val mediaItem: MediaItem, context: Context, adapter: CustomAdapter, dataset: ArrayList<String>){

    private val trackGroupsFuture = MetadataRetriever.retrieveMetadata(context, mediaItem)
    var trackGroup: TrackGroup? = null

    init {
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
                         //   if
                        }

                        dataset.add(resStr)

//                        0.until(trackGroups.length)
//                            .asSequence()
//                            .mapNotNull { trackGroups[it].getFormat(0).metadata}
////                            .filter { metadata -> metadata.length() == 1 }
////                            .map { it -> it. }
////                            .filterIsInstance<Metadata>()
//                            .forEachIndexed(){index, it ->
//                                Log.d("DBG_1","index: $index val: $it.toString()")
//                            }
                    }
                    adapter.notifyDataSetChanged()
                }

                override fun onFailure(t: Throwable) {
                    //handleFailure(t)
                }
            },
            context.mainExecutor
        )
    }

}

class MainActivity : AppCompatActivity() {

    private var itemsUri = listOf<Uri>()
    private var mediaSources = listOf<MediaItem>()
    private var tracksList = arrayListOf<TrackData>()

    var dataset = arrayListOf<String>("January", "February", "March")
    val customAdapter = CustomAdapter(dataset, onItemClicked = {
        Log.d("DBG_IC","You click $it")
        exoPlayer!!.seekTo(4,0)
    })

    private var exoPlayer: ExoPlayer? = null//exoPlayer

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



        val button = findViewById<Button>(R.id.button_choose)
        button.setOnClickListener {
            // Code here executes on main thread after user presses button
            Log.d("DBG","button clicked")
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                // Optionally, specify a URI for the directory that should be opened in
                // the system file picker when it loads.
                putExtra(DocumentsContract.EXTRA_INITIAL_URI, "")
            }

            startActivityForResult(intent, REQ_CODE)
        }



        val recyclerView: RecyclerView = findViewById(R.id.recycler_view)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = customAdapter


        val context = applicationContext
        exoPlayer = ExoPlayer.Builder(context).build()
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
                    var songInfo="";
                    mediaMetadata.artist?.let{Log.d("DBG",it.toString())
                        songInfo += it.toString()
                    }
                    mediaMetadata.albumTitle?.let{Log.d("DBG",it.toString())
                        songInfo += "-"
                        songInfo += it.toString()
                    }
                    mediaMetadata.title?.let{Log.d("DBG",it.toString())
                        songInfo += "-"
                        songInfo += it.toString()
                    }
                    findViewById<TextView>(R.id.id_title).setText(songInfo)
            }

            }
        )
    }

    override fun onDestroy() {
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
                val childDocuments = documentsTree.listFiles();//.toCachingList()
                Log.d("DBG","Number of childs ${childDocuments.size}")
                for(doc in childDocuments){
                    Log.d("DBG",doc.uri.toString())
                    itemsUri+=doc.uri

                }

                tracksList.clear()
                dataset.clear()

                for (uri in itemsUri){

                    val uri_str=uri.toString();
                    if(uri_str.contains("mp3")) {
                        Log.d("DBG_URI", uri.toString())
                        Log.d("DBG_URI", uri.normalizeScheme().toString())
//        val turi=Uri.parse("content://storage/9C33-6BBD%3AMusic%2FStolen%2FMetal%2FArtillery%2F1990%20-%20By%20Inheritance%2F02.%20Khomaniac.mp3")
//        val mediaItem=MediaItem.fromUri(turi);
                        val mediaItem = MediaItem.Builder().setUri(uri).build();
                        Log.d("DBG", mediaItem.mediaId)
                        Log.d("DBG", mediaItem.mediaMetadata.title.toString())
                        Log.d("DBG", mediaItem.toString())
//                        mediaItem.requestMetadata
                        mediaSources += mediaItem;
//                        dataset += uri.path.toString()
//                        dataset += mediaItem.mediaMetadata.title.toString()
//                        dataset += mediaItem.mediaMetadata.

                        tracksList += TrackData(mediaItem,applicationContext,customAdapter,dataset)

//                        val trackGroupsFuture = MetadataRetriever.retrieveMetadata(applicationContext, mediaItem)
//                        Futures.addCallback(
//                            trackGroupsFuture,
//                            object : FutureCallback<TrackGroupArray?> {
//                                override fun onSuccess(trackGroups: TrackGroupArray?) {
//                                    if (trackGroups != null) {//handleMetadata(trackGroups)
//                                        trackGroups.length
//                                    }
//                                }
//
//                                override fun onFailure(t: Throwable) {
//                                    //handleFailure(t)
//                                }
//                            },
//                            applicationContext.mainExecutor
//                        )

                    }
                }

//                customAdapter.notifyDataSetChanged()

                exoPlayer!!.setMediaItems(mediaSources)
                exoPlayer!!.prepare()
                exoPlayer!!.play()

                val count= exoPlayer!!.mediaItemCount
                for(c in 0..count - 1){
                    val item = exoPlayer!!.getMediaItemAt(c)
                    Log.d("DMG_MI", item.mediaMetadata.title.toString())
                }
            }
        }
    }

}