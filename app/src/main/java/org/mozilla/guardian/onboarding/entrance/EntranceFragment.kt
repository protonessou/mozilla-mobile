package org.mozilla.guardian.onboarding.entrance

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.Navigation
import kotlinx.android.synthetic.main.fragment_entrance.*
import org.mozilla.guardian.R

class EntranceFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_entrance, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        btn_auth.setOnClickListener(Navigation.createNavigateOnClickListener(R.id.action_auth))
        btn_intro.setOnClickListener(Navigation.createNavigateOnClickListener(R.id.action_intro))
    }
}