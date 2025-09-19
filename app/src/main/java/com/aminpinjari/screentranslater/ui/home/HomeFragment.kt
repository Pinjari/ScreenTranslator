package com.aminpinjari.screentranslater.ui.home

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.aminpinjari.screentranslater.R // Ensure R is imported for R.id.original_text
import com.aminpinjari.screentranslater.databinding.FragmentHomeBinding

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private var handler = Handler(Looper.getMainLooper())
    private var delayedRunnable: Runnable? = null

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

        val recyclerViewSentences: RecyclerView = binding.listViewSentences
        recyclerViewSentences.layoutManager = LinearLayoutManager(requireContext())
        val adapter = SentenceAdapter(sentences)
        recyclerViewSentences.adapter = adapter
        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // First dynamic TextView (created in previous steps)
        val dynamicTextViewOriginalText = "This is a Dynamically Added TextView!"
        val dynamicTextView = TextView(requireContext()).apply {
            id = View.generateViewId()
            text = dynamicTextViewOriginalText
            setTag(R.id.original_text, dynamicTextViewOriginalText)
            layoutParams = ConstraintLayout.LayoutParams(
                ConstraintLayout.LayoutParams.MATCH_CONSTRAINT,
                ConstraintLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = resources.getDimensionPixelSize(R.dimen.dp_16)
                bottomMargin = resources.getDimensionPixelSize(R.dimen.dp_16)
                marginStart = resources.getDimensionPixelSize(R.dimen.dp_8)
                marginEnd = resources.getDimensionPixelSize(R.dimen.dp_8)
            }
            textAlignment = View.TEXT_ALIGNMENT_CENTER
        }
        binding.homeConstraintLayout.addView(dynamicTextView)

        // New delayed TextView
        val delayedTextViewOriginalText = "This TextView Appears After 2 Seconds!"
        val delayedTextView = TextView(requireContext()).apply {
            id = View.generateViewId()
            text = delayedTextViewOriginalText
            setTag(R.id.original_text, delayedTextViewOriginalText)
            visibility = View.GONE // Initially hidden
            layoutParams = ConstraintLayout.LayoutParams(
                ConstraintLayout.LayoutParams.MATCH_CONSTRAINT,
                ConstraintLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = resources.getDimensionPixelSize(R.dimen.dp_16)
                bottomMargin = resources.getDimensionPixelSize(R.dimen.dp_16)
                marginStart = resources.getDimensionPixelSize(R.dimen.dp_8)
                marginEnd = resources.getDimensionPixelSize(R.dimen.dp_8)
            }
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            setBackgroundColor(android.graphics.Color.LTGRAY) // Optional: to make it visually distinct
        }
        binding.homeConstraintLayout.addView(delayedTextView)

        // Apply Constraints
        val constraintSet = ConstraintSet()
        constraintSet.clone(binding.homeConstraintLayout)

        // textView2 -> dynamicTextView
        constraintSet.connect(binding.textView2.id, ConstraintSet.BOTTOM, dynamicTextView.id, ConstraintSet.TOP)

        // dynamicTextView constraints
        constraintSet.connect(dynamicTextView.id, ConstraintSet.TOP, binding.textView2.id, ConstraintSet.BOTTOM)
        constraintSet.connect(dynamicTextView.id, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
        constraintSet.connect(dynamicTextView.id, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)
        constraintSet.connect(dynamicTextView.id, ConstraintSet.BOTTOM, delayedTextView.id, ConstraintSet.TOP)

        // delayedTextView constraints
        constraintSet.connect(delayedTextView.id, ConstraintSet.TOP, dynamicTextView.id, ConstraintSet.BOTTOM)
        constraintSet.connect(delayedTextView.id, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
        constraintSet.connect(delayedTextView.id, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)
        constraintSet.connect(delayedTextView.id, ConstraintSet.BOTTOM, binding.listViewSentences.id, ConstraintSet.TOP)

        // listViewSentences constraints
        constraintSet.clear(binding.listViewSentences.id, ConstraintSet.TOP) // Clear previous top constraint
        constraintSet.connect(binding.listViewSentences.id, ConstraintSet.TOP, delayedTextView.id, ConstraintSet.BOTTOM)

        constraintSet.applyTo(binding.homeConstraintLayout)

        // Post runnable to make the delayedTextView visible after 2 seconds
        delayedRunnable = Runnable {
            // Ensure view and binding are still valid
            if (_binding != null && isAdded) {
                delayedTextView.visibility = View.VISIBLE
            }
        }
        handler.postDelayed(delayedRunnable!!, 5000)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Remove any pending callbacks from the handler to prevent memory leaks or crashes
        delayedRunnable?.let { handler.removeCallbacks(it) }
        delayedRunnable = null
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
        val originalSentence = sentences[position]
        holder.textView.text = originalSentence
        holder.textView.setTag(R.id.original_text, originalSentence)
    }

    override fun getItemCount(): Int {
        return sentences.size
    }
}
