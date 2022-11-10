package com.github.jayteealao.crumbs.screens.login

@HiltViewModel
class LoginViewModel @Inject constructor(
) : ViewModel() {
    private var _isAccessTokenAvailable: MutableStateFlow<Boolean> = MutableStateFlow(false)
    val isAccessTokenAvailable: StateFlow<Boolean>
        get() = _isAccessTokenAvailable
