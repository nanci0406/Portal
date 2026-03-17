package moe.fuqiuluo.portal.ui.mock

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.CheckedTextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.Navigation
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.alibaba.fastjson2.JSON
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.fuqiuluo.portal.R
import moe.fuqiuluo.portal.android.root.ShellUtils
import moe.fuqiuluo.portal.android.widget.RockerView
import moe.fuqiuluo.portal.android.window.OverlayUtils
import moe.fuqiuluo.portal.databinding.FragmentRouteMockBinding
import moe.fuqiuluo.portal.ext.altitude
import moe.fuqiuluo.portal.ext.drawOverOtherAppsEnabled
import moe.fuqiuluo.portal.ext.hookSensor
import moe.fuqiuluo.portal.ext.jsonHistoricalRoutes
import moe.fuqiuluo.portal.ext.needOpenSELinux
import moe.fuqiuluo.portal.ext.selectRoute
import moe.fuqiuluo.portal.ext.speed
import moe.fuqiuluo.portal.service.MockServiceHelper
import moe.fuqiuluo.portal.ui.viewmodel.HomeViewModel
import moe.fuqiuluo.portal.ui.viewmodel.MockServiceViewModel
import moe.fuqiuluo.xposed.utils.FakeLoc
import androidx.navigation.findNavController

class RouteMockFragment : Fragment() {
    private var _binding: FragmentRouteMockBinding? = null
    private val binding get() = _binding!!

    private val routeMockViewModel by viewModels<HomeViewModel>()
    private val mockServiceViewModel by activityViewModels<MockServiceViewModel>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRouteMockBinding.inflate(inflater, container, false)

        if (mockServiceViewModel.isServiceStart()) {
            binding.switchMock.text = "停止模拟"
            ContextCompat.getDrawable(requireContext(), R.drawable.rounded_play_disabled_24)?.let {
                binding.switchMock.icon = it
            }
        }

        binding.switchMock.setOnClickListener {
            if (mockServiceViewModel.isServiceStart()) {
                tryCloseService(it as MaterialButton)
            } else {
                tryOpenService(it as MaterialButton)
            }
        }

        with(mockServiceViewModel) {
            if (rocker.isStart) {
                binding.rocker.toggle()
            }
            binding.rocker.setOnClickListener {
                if (locationManager == null) {
                    Toast.makeText(requireContext(), "定位服务加载异常", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if (!isServiceStart()) {
                    Toast.makeText(requireContext(), "请先启动模拟", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val checkedTextView = it as CheckedTextView
                checkedTextView.toggle()

                if (!requireContext().drawOverOtherAppsEnabled()) {
                    Toast.makeText(requireContext(), "请授权悬浮窗权限", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                lifecycleScope.launch(Dispatchers.Main) {
                    if (checkedTextView.isChecked) {
                        rocker.show()
                    } else {
                        rocker.hide()
                        rockerCoroutineController.pause()
                    }
                }
            }
            rocker.setRockerListener(object : RockerView.Companion.OnMoveListener {
                override fun onAngle(angle: Double) {
                    MockServiceHelper.setBearing(locationManager!!, angle)
                    FakeLoc.bearing = angle
                    FakeLoc.hasBearings = true
                }

                override fun onLockChanged(isLocked: Boolean) {
                    isRockerLocked = isLocked
                }

                override fun onFinished() {
                    if (!isRockerLocked) {
                        rockerCoroutineController.pause()
                    }
                }

                override fun onStarted() {
                    rockerCoroutineController.resume()
                }
            })
            rocker.setRockerAutoListener(object : Rocker.Companion.OnAutoListener {
                override fun onAutoPlay(isPlay: Boolean) {
                    if (isPlay) {
                        routeMockCoroutine.resume()
                    } else {
                        routeMockCoroutine.pause()
                    }
                }

                override fun onAutoLock(isLock: Boolean) {

                }
            })
        }

        requireContext().selectRoute?.let {
            binding.mockRouteName.text = it.name
            mockServiceViewModel.selectedRoute = it
        }


        binding.fab.setOnClickListener { view ->
            val subFabList = arrayOf(
                binding.fabAddRoute
            )

            if (!routeMockViewModel.mFabOpened) {
                routeMockViewModel.mFabOpened = true

                val rotateMainFab = ObjectAnimator.ofFloat(view, "rotation", 0f, 90f)
                rotateMainFab.duration = 200

                val animators = arrayListOf<ObjectAnimator>()
                animators.add(rotateMainFab)
                subFabList.forEachIndexed { index, fab ->
                    fab.visibility = View.VISIBLE
                    fab.alpha = 1f
                    fab.scaleX = 1f
                    fab.scaleY = 1f
                    val translationX =
                        ObjectAnimator.ofFloat(fab, "translationX", 0f, 20f + index * 8f)
                    translationX.duration = 200
                    animators.add(translationX)
                }

                val animatorSet = AnimatorSet()
                animatorSet.playTogether(animators.toList())
                animatorSet.interpolator = DecelerateInterpolator()
                animatorSet.addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        view.isClickable = true
                    }
                })
                view.isClickable = false
                animatorSet.start()
            } else {
                routeMockViewModel.mFabOpened = false

                val rotateMainFab = ObjectAnimator.ofFloat(view, "rotation", 90f, 0f)
                rotateMainFab.duration = 200

                val animators = arrayListOf<ObjectAnimator>()
                animators.add(rotateMainFab)
                subFabList.forEachIndexed { index, fab ->
                    val transX = ObjectAnimator.ofFloat(fab, "translationX", 0f, -20f - index * 8f)
                    transX.duration = 150
                    val scaleX = ObjectAnimator.ofFloat(fab, "scaleX", 1f, 0f)
                    scaleX.duration = 200
                    val scaleY = ObjectAnimator.ofFloat(fab, "scaleY", 1f, 0f)
                    scaleY.duration = 200
                    val alpha = ObjectAnimator.ofFloat(fab, "alpha", 1f, 0f)
                    alpha.duration = 200
                    animators.add(transX)
                    animators.add(scaleX)
                    animators.add(scaleY)
                    animators.add(alpha)
                }

                val animatorSet = AnimatorSet()
                animatorSet.playTogether(animators.toList())
                animatorSet.interpolator = DecelerateInterpolator()
                animatorSet.addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        subFabList.forEach { it.visibility = View.GONE }
                        view.isClickable = true
                    }
                })
                view.isClickable = false
                animatorSet.start()
            }
        }

        binding.fabAddRoute.setOnClickListener {
            activity?.findNavController(R.id.nav_host_fragment_content_main)?.navigate(R.id.nav_route_edit)
        }

        var locations = requireContext().jsonHistoricalRoutes
//        val routes = Json.decodeFromString<List<HistoricalRoute>>(locations)
        // 如果locations是空字符串，则创建默认
        if (locations.isEmpty()) {
            val defaultRoute = HistoricalRoute(
                "默认路线",
                mutableListOf(
                    Pair(34.802529, 113.790286),
                    Pair(34.802811, 113.790034),
                    Pair(34.802905, 113.789866),
                    Pair(34.802866, 113.789561),
                    Pair(34.802737, 113.78936),
                    Pair(34.80256, 113.789229),
                    Pair(34.80233, 113.789383),
                    Pair(34.801805, 113.789817),
                    Pair(34.801651, 113.789944),
                    Pair(34.801623, 113.79013),
                    Pair(34.801641, 113.790292),
                    Pair(34.801746, 113.790473),
                    Pair(34.802004, 113.790614),
                    Pair(34.802369, 113.790441),
                    Pair(34.802578, 113.790219),
                    Pair(34.80291, 113.789909),
                    Pair(34.802888, 113.789597),
                    Pair(34.802497, 113.789244),
                    Pair(34.801647, 113.789926),
                    Pair(34.801685, 113.790396),
                    Pair(34.802131, 113.790606),
                    Pair(34.802787, 113.790047),
                    Pair(34.802903, 113.789554),
                    Pair(34.802645, 113.789351),
                    Pair(34.802433, 113.789227),
                    Pair(34.801931, 113.789634),
                    Pair(34.801713, 113.789877),
                    Pair(34.801518, 113.790154),
                    Pair(34.801656, 113.790476),
                    Pair(34.801924, 113.790655),
                    Pair(34.802328, 113.790476),
                    Pair(34.80252, 113.79023),
                    Pair(34.802925, 113.789838),
                    Pair(34.802827, 113.789423),
                    Pair(34.802751, 113.789571),
                    Pair(34.802569, 113.78924),
                    Pair(34.802039, 113.789559),
                    Pair(34.80182, 113.789783),
                    Pair(34.801582, 113.790062),
                    Pair(34.801658, 113.790355),
                    Pair(34.801908, 113.790586),
                    Pair(34.802121, 113.7907),
                    Pair(34.802359, 113.790439),
                    Pair(34.802577, 113.79019099999999),
                    Pair(34.802837, 113.789955),
                    Pair(34.802933, 113.789867),
                    Pair(34.802801, 113.789423),
                    Pair(34.802524, 113.789255),
                    Pair(34.802295, 113.789386),
                    Pair(34.802026, 113.789612),
                    Pair(34.801578, 113.790094),
                    Pair(34.801768, 113.790557),
                    Pair(34.802276, 113.790593),
                    Pair(34.802751, 113.79012),
                    Pair(34.802937, 113.789732),
                    Pair(34.802769, 113.789417),
                    Pair(34.802536, 113.789182),
                    Pair(34.801887, 113.78964),
                    Pair(34.801637, 113.789934),
                    Pair(34.80141, 113.790212),
                    Pair(34.80167, 113.79043),
                    Pair(34.801878, 113.790662),
                    Pair(34.802169, 113.790543),
                    Pair(34.802399, 113.790321),
                    Pair(34.80266, 113.790117),
                    Pair(34.802914, 113.78987)
                )
            )
            val defaultRoutes = mutableListOf(defaultRoute)
            requireContext().jsonHistoricalRoutes = JSON.toJSONString(defaultRoutes)
            locations = requireContext().jsonHistoricalRoutes
        }
        val routes = JSON.parseArray(locations, HistoricalRoute::class.java)

        val historicalRouteAdapter = HistoricalRouteAdapter(routes.sortedBy { it.name }
            .toMutableList()) { route, isLongClick ->
            if (isLongClick) {
                Toast.makeText(requireContext(), "长按", Toast.LENGTH_SHORT).show()
            } else {
                binding.mockRouteName.text = route.name
                mockServiceViewModel.selectedRoute = route
                requireContext().selectRoute = route

                if (MockServiceHelper.isMockStart(mockServiceViewModel.locationManager!!)) {
                    // 获取第一个点
                    val first = route.route[0]
                    if (MockServiceHelper.setLocation(
                            mockServiceViewModel.locationManager!!,
                            first.first,
                            first.second
                        )
                    ) {
                        Toast.makeText(requireContext(), "位置更新成功", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(requireContext(), "更新位置失败", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }


        val recyclerView = binding.historicalRouteList
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = historicalRouteAdapter

        ItemTouchHelper(object :
            ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                return false
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                val location = historicalRouteAdapter[position]
                with(requireContext()) {
                    MaterialAlertDialogBuilder(this)
                        .setTitle("删除路线")
                        .setMessage("确定要删除路线(${location.name})吗？")
                        .setPositiveButton("删除") { _, _ ->
                            historicalRouteAdapter.removeItem(position)
                            JSON.parseArray(jsonHistoricalRoutes, HistoricalRoute::class.java)
                                .toMutableList().apply {
                                    removeIf { it.name == location.name }
                                }.let {
                                    jsonHistoricalRoutes = JSON.toJSONString(it)
                                }
                            showToast("已删除路线")
                        }
                        .setNegativeButton("取消", { _, _ ->
                            historicalRouteAdapter.notifyItemChanged(position)
                        })
                        .show()
                }
            }
        }).attachToRecyclerView(recyclerView)

        return binding.root
    }


    private fun tryOpenService(button: MaterialButton) {
        if (!OverlayUtils.hasOverlayPermissions(requireContext())) {
            showToast("请授权悬浮窗权限")
            return
        }

        val selectedRoute = mockServiceViewModel.selectedRoute ?: run {
            showToast("请选择一个路线")
            return
        }

        if (mockServiceViewModel.locationManager == null) {
            showToast("定位服务加载异常")
            return
        }

        if (!MockServiceHelper.isServiceInit()) {
            showToast("系统服务注入失败")
            return
        }

        lifecycleScope.launch {
            val context = requireContext()
            val speed = context.speed
            val altitude = context.altitude
            val accuracy = FakeLoc.accuracy

            button.isClickable = false
            try {
                withContext(Dispatchers.IO) {
                    if (MockServiceHelper.tryOpenMock(
                            mockServiceViewModel.locationManager!!,
                            speed,
                            altitude,
                            accuracy
                        )
                    ) {
                        updateMockButtonState(
                            button,
                            "停止模拟",
                            R.drawable.rounded_play_disabled_24
                        )
                    } else {
                        showToast("模拟服务启动失败")
                        return@withContext
                    }

                    val first = selectedRoute.route[0]
                    if (MockServiceHelper.setLocation(
                            mockServiceViewModel.locationManager!!,
                            first.first,
                            first.second
                        )
                    ) {
                        showToast("更新路线起点位置成功")
                    } else {
                        showToast("更新位置失败")
                    }
                }
            } finally {
                button.isClickable = true
            }
        }


    }

    private fun tryCloseService(button: MaterialButton) {
        if (mockServiceViewModel.locationManager == null) {
            showToast("定位服务加载异常")
            return
        }

        if (!MockServiceHelper.isServiceInit()) {
            showToast("系统服务注入失败")
            return
        }

        lifecycleScope.launch {
            button.isClickable = false
            try {
                val isClosed = withContext(Dispatchers.IO) {
                    if (!MockServiceHelper.isMockStart(mockServiceViewModel.locationManager!!)) {
                        showToast("模拟服务未启动")
                        return@withContext false
                    }

                    if (MockServiceHelper.tryCloseMock(mockServiceViewModel.locationManager!!)) {
                        updateMockButtonState(button, "开始模拟", R.drawable.rounded_play_arrow_24)
                        return@withContext true
                    } else {
                        showToast("模拟服务停止失败")
                        return@withContext false
                    }
                }
                if (isClosed && mockServiceViewModel.rocker.isStart) {
                    binding.rocker.isClickable = false
                    binding.rocker.toggle()
                    mockServiceViewModel.rocker.hide()
                    mockServiceViewModel.rockerCoroutineController.pause()
                    binding.rocker.isClickable = true
                }
            } finally {
                button.isClickable = true
            }
        }
    }


    private fun showToast(message: String) = lifecycleScope.launch(Dispatchers.Main) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    private fun updateMockButtonState(button: MaterialButton, text: String, iconRes: Int) =
        lifecycleScope.launch(Dispatchers.Main) {
            button.text = text
            ContextCompat.getDrawable(requireContext(), iconRes)?.let {
                button.icon = it
            }
        }

}
