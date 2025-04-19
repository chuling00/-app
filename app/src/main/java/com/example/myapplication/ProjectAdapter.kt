import android.content.Context
import android.view.View
import androidx.recyclerview.widget.RecyclerView

abstract class ProjectAdapter(private val context: Context, private var projects: List<String>) :
    RecyclerView.Adapter<ProjectAdapter.ViewHolder>() {
    
    private var onItemClickListener: ((Int) -> Unit)? = null
    private var onItemLongClickListener: ((Int) -> Boolean)? = null

    fun setOnItemLongClickListener(listener: (Int) -> Boolean) {
        onItemLongClickListener = listener
    }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        init {
            itemView.setOnClickListener {
                onItemClickListener?.invoke(adapterPosition)
            }
            
            itemView.setOnLongClickListener {
                onItemLongClickListener?.invoke(adapterPosition) ?: false
            }
        }
    }
} 