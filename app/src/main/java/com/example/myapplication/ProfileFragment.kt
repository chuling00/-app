package com.example.myapplication

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import android.widget.ImageView

class ProfileFragment : Fragment() {

    private lateinit var ivAvatar: ImageView
    private lateinit var tvNickname: TextView
    private lateinit var tvSettings: TextView
    private lateinit var tvAbout: TextView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_profile, container, false)
        
        ivAvatar = view.findViewById(R.id.ivAvatar)
        tvNickname = view.findViewById(R.id.tvNickname)
        tvSettings = view.findViewById(R.id.tvSettings)
        tvAbout = view.findViewById(R.id.tvAbout)
        
        setupListeners()
        
        return view
    }
    
    private fun setupListeners() {
        ivAvatar.setOnClickListener {
            Toast.makeText(requireContext(), "修改头像功能开发中", Toast.LENGTH_SHORT).show()
        }
        
        tvNickname.setOnClickListener {
            Toast.makeText(requireContext(), "修改昵称功能开发中", Toast.LENGTH_SHORT).show()
        }
        
        tvSettings.setOnClickListener {
            Toast.makeText(requireContext(), "设置功能开发中", Toast.LENGTH_SHORT).show()
        }
        
        tvAbout.setOnClickListener {
            Toast.makeText(requireContext(), "关于功能开发中", Toast.LENGTH_SHORT).show()
        }
    }
} 