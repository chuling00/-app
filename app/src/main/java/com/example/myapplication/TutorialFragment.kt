package com.example.myapplication

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment

/**
 * 教学页面Fragment
 */
class TutorialFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // 加载教学页面布局
        return inflater.inflate(R.layout.fragment_tutorial, container, false)
    }

    companion object {
        fun newInstance(): TutorialFragment {
            return TutorialFragment()
        }
    }
} 