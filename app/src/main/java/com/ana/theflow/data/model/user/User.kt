package com.ana.theflow.data.model.user

import com.ana.theflow.utilities.Constants

data class User(
    val uid: String = "",
    val firstName: String = "",
    val lastName: String = "",
    val email: String = "",
    val role: String = Constants.UserRole.DANCER.name
)
