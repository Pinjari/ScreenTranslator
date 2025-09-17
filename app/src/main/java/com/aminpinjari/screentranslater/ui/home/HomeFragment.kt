package com.aminpinjari.screentranslater.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.aminpinjari.screentranslater.databinding.FragmentHomeBinding

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val homeViewModel =
            ViewModelProvider(this).get(HomeViewModel::class.java)

        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        val root: View = binding.root

        val textView: TextView = binding.textHome
        homeViewModel.text.observe(viewLifecycleOwner) {
            textView.text = it
        }

        // List of English sentences starting with letters A-Z
        val sentences = listOf(
            "Apples are a healthy fruit.",
            "Birds sing sweetly in the morning.",
            "Cats are known for their independence.",
            "Dogs are loyal companions.",
            "Elephants are the largest land animals.",
            "Flowers bloom in the spring.",
            "Grapes grow in bunches.",
            "Horses are majestic animals.",
            "Ice cream is a popular dessert.",
            "Jaguars are fast runners.",
            "Kangaroos carry their young in pouches.",
            "Lions are known as the king of the jungle.",
            "Monkeys are playful creatures.",
            "Nightingales sing beautiful songs.",
            "Owls are nocturnal birds.",
            "Penguins live in cold climates.",
            "Quails are small birds.",
            "Rabbits have long ears.",
            "Snakes slither on the ground.",
            "Tigers are striped cats.",
            "Umbrellas protect us from the rain.",
            "Violets are purple flowers.",
            "Whales are the largest mammals.",
            "Xylophones make musical sounds.",
            "Yaks live in mountainous regions.",
            "Zebras have black and white stripes."
        )
        // Find the RecyclerView in your layout
        val recyclerViewSentences: RecyclerView = binding.listViewSentences
        recyclerViewSentences.layoutManager = LinearLayoutManager(requireContext())
        // Create an adapter to display the list of sentences
        val adapter = SentenceAdapter(sentences)
        recyclerViewSentences.adapter = adapter
        return root
    }
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

class SentenceAdapter(private val sentences: List<String>) :
    RecyclerView.Adapter<SentenceAdapter.SentenceViewHolder>() {

    class SentenceViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val textView: TextView = itemView.findViewById(android.R.id.text1)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SentenceViewHolder {
        val itemView = LayoutInflater.from(parent.context)
            .inflate(android.R.layout.simple_list_item_1, parent, false)
        return SentenceViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: SentenceViewHolder, position: Int) {
        holder.textView.text = sentences[position]
    }

    override fun getItemCount(): Int {
        return sentences.size
    }
}