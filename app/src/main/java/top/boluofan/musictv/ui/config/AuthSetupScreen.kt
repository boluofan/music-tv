package top.boluofan.musictv.ui.config

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.boluofan.musictv.ui.components.generateQrBitmap
import top.boluofan.musictv.ui.search.TvKeyboard
import top.boluofan.musictv.ui.search.TvKeyboardMode

private enum class ActiveField { NONE, SERVER_URL, USERNAME, PASSWORD }

@Composable
fun AuthSetupScreen(
    viewModel: AuthViewModel = hiltViewModel()
) {
    val authState by viewModel.authState.collectAsStateWithLifecycle()

    when (authState) {
        is AuthState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("加载中...", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        }
        is AuthState.LoggedIn -> { /* 已登录，由 MainActivity 切走 */ }
        else -> LoginForm(viewModel)
    }
}

@Composable
private fun LoginForm(viewModel: AuthViewModel) {
    val serverUrl by viewModel.serverUrl.collectAsStateWithLifecycle()
    val username by viewModel.username.collectAsStateWithLifecycle()
    val password by viewModel.password.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoggingIn.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val configUrl by viewModel.configUrl.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.startConfigServer() }

    var activeField by remember { mutableStateOf(ActiveField.NONE) }
    var passwordVisible by remember { mutableStateOf(false) }
    val showKeyboard = activeField != ActiveField.NONE
    val keyboardFocus = remember { FocusRequester() }
    val serverUrlFocus = remember { FocusRequester() }
    val usernameFocus = remember { FocusRequester() }
    val passwordFocus = remember { FocusRequester() }
    var lastActiveField by remember { mutableStateOf(ActiveField.NONE) }

    val windowInfo = LocalWindowInfo.current
    val view = LocalView.current

    LaunchedEffect(Unit) {
        // 冷启动时窗口已聚焦但 AndroidComposeView 尚无 view 焦点，
        // 此时 Compose 的 requestOwnerFocus() 会失败导致焦点请求被静默丢弃，需先补 view 焦点
        repeat(60) {
            withFrameNanos { }
            if (windowInfo.isWindowFocused) {
                view.requestFocus()
                serverUrlFocus.requestFocus()
                return@LaunchedEffect
            }
        }
    }

    LaunchedEffect(showKeyboard) {
        if (showKeyboard) {
            lastActiveField = activeField
            runCatching { keyboardFocus.requestFocus() }
        } else {
            runCatching {
                when (lastActiveField) {
                    ActiveField.SERVER_URL -> serverUrlFocus.requestFocus()
                    ActiveField.USERNAME -> usernameFocus.requestFocus()
                    ActiveField.PASSWORD -> passwordFocus.requestFocus()
                    ActiveField.NONE -> {}
                }
            }
        }
    }

    BackHandler(enabled = showKeyboard) {
        activeField = ActiveField.NONE
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.weight(1f)) {
        // 表单区域（可滚动）
        Column(
            modifier = Modifier
                .weight(0.6f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 56.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(24.dp))
            Text("菠萝音乐", fontSize = 36.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            Text("连接到 lxserver 服务器并登录", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            Spacer(Modifier.height(32.dp))

            InputField(
                label = "服务器地址",
                value = serverUrl,
                placeholder = "http://192.168.1.100:9527",
                focusRequester = serverUrlFocus,
                isActive = activeField == ActiveField.SERVER_URL,
                onTextChange = viewModel::onServerUrlChanged,
                onActivate = { activeField = ActiveField.SERVER_URL }
            )

            Spacer(Modifier.height(16.dp))

            InputField(
                label = "账号",
                value = username,
                placeholder = "lxserver用户名",
                focusRequester = usernameFocus,
                isActive = activeField == ActiveField.USERNAME,
                onTextChange = viewModel::onUsernameChanged,
                onActivate = { activeField = ActiveField.USERNAME }
            )

            Spacer(Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    InputField(
                        label = "密码",
                        value = password,
                        placeholder = "输入密码",
                        focusRequester = passwordFocus,
                        isPassword = true,
                        passwordVisible = passwordVisible,
                        isActive = activeField == ActiveField.PASSWORD,
                        onTextChange = viewModel::onPasswordChanged,
                        onActivate = { activeField = ActiveField.PASSWORD }
                    )
                }
                Spacer(Modifier.width(8.dp))
                var eyeFocused by remember { mutableStateOf(false) }
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (eyeFocused) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .then(
                            if (eyeFocused) Modifier.border(
                                3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp)
                            ) else Modifier
                        )
                        .onFocusChanged { eyeFocused = it.isFocused }
                        .clickable { passwordVisible = !passwordVisible },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Rounded.Visibility
                                      else Icons.Rounded.VisibilityOff,
                        contentDescription = if (passwordVisible) "隐藏密码" else "显示密码",
                        tint = if (eyeFocused) MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            val errorMsg = error
            if (!errorMsg.isNullOrEmpty()) {
                Text(
                    errorMsg, fontSize = 14.sp, color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
            }

            var btnFocused by remember { mutableStateOf(false) }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isLoading) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.primary)
                    .then(
                        if (btnFocused) Modifier.border(
                            3.dp, MaterialTheme.colorScheme.onPrimary, RoundedCornerShape(12.dp)
                        ) else Modifier
                    )
                    .onFocusChanged { btnFocused = it.isFocused }
                    .clickable(enabled = !isLoading) { viewModel.login() }
                    .padding(horizontal = 56.dp, vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (isLoading) "连接并登录中..." else "连接并登录",
                    fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary
                )
            }
            Spacer(Modifier.height(16.dp))
        }

        configUrl?.let { url ->
            QrPanel(
                url = url,
                modifier = Modifier
                    .weight(0.4f)
                    .fillMaxHeight()
                    .padding(end = 48.dp, top = 24.dp, bottom = 24.dp)
            )
        }
        }

    }

    if (showKeyboard) {
        // 全屏遮罩：点击非键盘区域关闭自定义键盘；返回键直接关闭（避免焦点/返回栈抢用导致需多次按键）
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && event.key == Key.Back) {
                        activeField = ActiveField.NONE
                        true
                    } else false
                }
                .clickable { activeField = ActiveField.NONE }
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .clickable { }
            ) {
                // 键盘区域（回显栏 + 自定义键盘）
                val echoLabel = when (activeField) {
                    ActiveField.SERVER_URL -> "服务器地址"
                    ActiveField.USERNAME -> "账号"
                    ActiveField.PASSWORD -> "密码"
                    else -> ""
                }
                val echoText = when (activeField) {
                    ActiveField.SERVER_URL -> serverUrl
                    ActiveField.USERNAME -> username
                    ActiveField.PASSWORD -> password
                    else -> ""
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 32.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "$echoLabel：",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Text(
                        if (echoText.isEmpty()) "（未输入）" else echoText,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (echoText.isEmpty())
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        else MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                TvKeyboard(
                    mode = TvKeyboardMode.LOGIN,
                    firstKeyFocusRequester = keyboardFocus,
                    onKeyPress = { key ->
                        val current = when (activeField) {
                            ActiveField.SERVER_URL -> serverUrl
                            ActiveField.USERNAME -> username
                            ActiveField.PASSWORD -> password
                            else -> return@TvKeyboard
                        }
                        val newValue = when (key) {
                            "←退格" -> if (current.isNotEmpty()) current.substring(0, current.length - 1) else current
                            "清空" -> ""
                            "确定" -> { activeField = ActiveField.NONE; return@TvKeyboard }
                            "空格" -> "$current "
                            else -> "$current$key"
                        }
                        when (activeField) {
                            ActiveField.SERVER_URL -> viewModel.onServerUrlChanged(newValue)
                            ActiveField.USERNAME -> viewModel.onUsernameChanged(newValue)
                            ActiveField.PASSWORD -> viewModel.onPasswordChanged(newValue)
                            else -> {}
                        }
                    }
                )
            }
        }
    }
}
}

@Composable
private fun QrPanel(url: String, modifier: Modifier = Modifier) {
    val qrBitmap = remember(url) { generateQrBitmap(url) }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "手机扫码配置",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "手机与电视处于同一局域网时，\n可手机扫码配置登录",
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
        Spacer(Modifier.height(16.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White)
                .padding(10.dp)
        ) {
            Image(
                bitmap = qrBitmap.asImageBitmap(),
                contentDescription = "扫码配置",
                modifier = Modifier.size(180.dp)
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = url,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
        )
    }
}

@Composable
private fun InputField(
    label: String,
    value: String,
    placeholder: String,
    focusRequester: FocusRequester,
    isPassword: Boolean = false,
    passwordVisible: Boolean = false,
    isActive: Boolean = false,
    onTextChange: (String) -> Unit = {},
    onActivate: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }

    Text(
        label, fontSize = 14.sp, fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(8.dp))

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                else if (focused) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .then(
                if (focused) Modifier.border(
                    3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp)
                ) else Modifier
            )
            // 仅确认键（点击）激活自定义键盘，焦点经过不弹出
            .onFocusChanged { focused = it.isFocused }
            .clickable { onActivate() }
            .focusRequester(focusRequester)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = when {
                value.isNotEmpty() && isPassword && !passwordVisible -> "●".repeat(value.length)
                value.isNotEmpty() -> value
                else -> placeholder
            },
            fontSize = 16.sp,
            color = if (value.isNotEmpty()) MaterialTheme.colorScheme.onBackground
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
        )
    }
}
