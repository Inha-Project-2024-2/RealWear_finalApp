package com.example.claude
import androidx.recyclerview.widget.RecyclerView
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.view.ViewGroup
import android.view.LayoutInflater


class ContactAdapter(private val contactList: List<Contact>,
                        private val onContactClick: (Contact) -> Unit
) : RecyclerView.Adapter<ContactAdapter.ContactViewHolder>() {

    inner class ContactViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val contactIcon: ImageView = itemView.findViewById(R.id.contactIcon)
        val contactName: TextView = itemView.findViewById(R.id.contactName)

        init{
            itemView.setOnClickListener {
                onContactClick(contactList[adapterPosition])
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ContactViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.contact_item, parent, false)
        return ContactViewHolder(view)
    }

    override fun onBindViewHolder(holder: ContactViewHolder, position: Int) {
        val contact = contactList[position]
        holder.contactIcon.setImageResource(contact.iconResId)
        holder.contactName.text = contact.name
    }

    override fun getItemCount(): Int {
        return contactList.size
    }
}
