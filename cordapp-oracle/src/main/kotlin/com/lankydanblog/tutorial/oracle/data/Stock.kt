package com.lankydanblog.tutorial.oracle.data

data class Stock(
  val symbol: String,
  val name: String,
  val primaryExchange: String,
  val price: Double
)