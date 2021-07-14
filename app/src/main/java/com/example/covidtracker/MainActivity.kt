package com.example.covidtracker

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import com.example.covidtracker.Model.CovidData
import com.example.covidtracker.Model.CovidSparkAdapter
import com.example.covidtracker.Model.Metric
import com.example.covidtracker.Model.TimeScale
import com.example.covidtracker.api.CovidService
import com.google.gson.GsonBuilder
import com.robinhood.spark.SparkView
import com.robinhood.ticker.TickerUtils
import com.robinhood.ticker.TickerView
import org.angmarch.views.NiceSpinner
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

private const val BASE_URL="https://api.covidtracking.com/v1/"
private const val ALL_STATES: String="ALL (Nationwide)"
class MainActivity : AppCompatActivity() {
    private lateinit var currentlyShownData: List<CovidData>
    private lateinit var adapter: CovidSparkAdapter
    private lateinit var MetricLabel:TickerView
    private lateinit var DateLabel:TextView
    private lateinit var RadioGroupTime:RadioGroup
    private lateinit var Week:RadioButton
    private lateinit var Month:RadioButton
    private lateinit var Max:RadioButton
    private lateinit var RadioGroupMetrics:RadioGroup
    private lateinit var negative:RadioButton
    private lateinit var positive:RadioButton
    private lateinit var death:RadioButton
    private lateinit var RadioGroupMetric:RadioGroup
    private lateinit var Negative:RadioButton
    private lateinit var Positive:RadioButton
    private lateinit var Death:RadioButton
    private lateinit var sparkview:SparkView
    private lateinit var spinner:NiceSpinner
    private lateinit var selectState:TextView
    private lateinit var perStateDailyData: Map<String, List<CovidData>>
    private lateinit var nationalDailyData: List<CovidData>
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        supportActionBar?.title = getString(R.string.app_description)
        InitView()
        val TAG = "tag"
        val gson = GsonBuilder().setDateFormat("yyyy-MM-dd'T'HH:mm:ss").create()

        val retrofit = Retrofit.Builder()
                       .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
        val covidService = retrofit.create(CovidService::class.java)

        //fetch the national data
        covidService.getNationalData().enqueue(object : Callback<List<CovidData>> {
            override fun onResponse(
                call: Call<List<CovidData>>,
                response: Response<List<CovidData>>
            ) {
                Log.e(TAG, "onResponse: $response",)
                val nationalData = response.body()
                if (nationalData == null) {
                    Log.w(TAG, "Did not receive a valid response body ",)
                    return
                }
                setupEventListeners()
                nationalDailyData = nationalData.reversed()
                Log.i(TAG, "Update graph with national Data")
                updateDisplayWithData(nationalDailyData)
            }
            override fun onFailure(call: Call<List<CovidData>>, t: Throwable) {
                Log.e(TAG, "onFailure: $t",)
            }
        })

        //fetch the state data
        covidService.getStatesData().enqueue(object : Callback<List<CovidData>> {
            override fun onResponse(
                call: Call<List<CovidData>>,
                response: Response<List<CovidData>>
            ) {
                Log.e(TAG, "onResponse: $response",)
                val stateData = response.body()
                if (stateData == null) {
                    Log.w(TAG, "Did not receive a valid response body ",)
                    return
                }
                perStateDailyData = stateData.reversed().groupBy { it.state }
                Log.i(TAG, "Update spinner with state names")

                //Update spinner with state names
                updateSpinnerWithStateData(perStateDailyData.keys)
            }
            override fun onFailure(call: Call<List<CovidData>>, t: Throwable) {
                Log.e(TAG, "onFailure: $t",)
            }
        })

    }

    private fun updateSpinnerWithStateData(stateName: Set<String>) {
      val stateAbbreviationList = stateName.toMutableList()
        stateAbbreviationList.sort()
        stateAbbreviationList.add(0,ALL_STATES)

        //add state list as DATA source for the spinner
        spinner.attachDataSource(stateAbbreviationList)
        spinner.setOnSpinnerItemSelectedListener { parent, _, position, _ ->
            val selectedState = parent.getItemAtPosition(position) as String
            val selectedData = perStateDailyData[selectedState] ?: nationalDailyData
            updateDisplayWithData(selectedData)
        }

    }

    private fun setupEventListeners() {
        MetricLabel.setCharacterLists(TickerUtils.provideNumberList())
        //add a listener for the user scrubbing on the chart
        sparkview.isScrubEnabled = true
        sparkview.setScrubListener { itemData ->
            if (itemData is CovidData){
                updateInfoForDate(itemData)
            }
        }

        //radio button selected events
        RadioGroupTime.setOnCheckedChangeListener { _, checkedId ->
            adapter.dayAgo = when(checkedId){
                R.id.radioButtonWeek -> TimeScale.WEEK
                R.id.radioButtonMonth -> TimeScale.MONTH
                else -> TimeScale.MAX
            }
            adapter.notifyDataSetChanged()
        }

        RadioGroupMetric.setOnCheckedChangeListener { _, checkedId ->
            when(checkedId){
                R.id.radioButtonNegative -> updateDisplayMetric(Metric.NEGATIVE)
                R.id.radioButtonPositive -> updateDisplayMetric(Metric.POSITIVE)
                R.id.radioButtonDeath -> updateDisplayMetric(Metric.DEATH)
            }
        }
    }

    private fun updateDisplayMetric(metric: Metric) {
        //update the color of the chart
        val colorRes = when(metric){
            Metric.NEGATIVE -> R.color.color_negative
            Metric.POSITIVE -> R.color.color_positive
            Metric.DEATH -> R.color.color_death

        }
        @ColorInt val colorInt = ContextCompat.getColor(this , colorRes)
        sparkview.lineColor = colorInt
        MetricLabel.setTextColor(colorInt)
        //updating the metric on the number
        adapter.metric = metric
        adapter.notifyDataSetChanged()
        //REset number and date shown in the bottom text view
        updateInfoForDate(currentlyShownData.last())
    }

    private fun updateDisplayWithData(dailyData: List<CovidData>) {
        currentlyShownData = dailyData
      //create new sparkAdapter with the data
        adapter = CovidSparkAdapter(dailyData)
        sparkview.adapter = adapter
        //update radio buttons to select the positive cases and max time by default
        positive.isChecked = true
        Max.isChecked = true
        //display metric for the most recent date
        updateDisplayMetric(Metric.POSITIVE)
    }
    private fun updateInfoForDate(covidData: CovidData) {
        val numCases = when(adapter.metric){
            Metric.NEGATIVE -> covidData.negativeIncrease
            Metric.POSITIVE -> covidData.positiveIncrease
            Metric.DEATH -> covidData.deathIncrease
        }
     MetricLabel.text = NumberFormat.getInstance().format(numCases)
        val outputDateFormate= SimpleDateFormat("MMM dd, yyyy", Locale.US)
       DateLabel.text = outputDateFormate.format(covidData.dateChecked)
    }



    fun InitView(){
        MetricLabel = findViewById(R.id.tvMetricLabel)
        DateLabel = findViewById(R.id.tvDateLabel)
        RadioGroupTime = findViewById(R.id.radioGroupTimeSelection)
        Week = findViewById(R.id.radioButtonWeek)
        Month = findViewById(R.id.radioButtonMonth)
        Max = findViewById(R.id.radioButtonMax)
        RadioGroupMetric = findViewById(R.id.radioGroupMetricSelection)
        negative = findViewById(R.id.radioButtonNegative)
        positive = findViewById(R.id.radioButtonPositive)
        death = findViewById(R.id.radioButtonDeath)
        sparkview = findViewById(R.id.sparkview)
        selectState = findViewById(R.id.tvSelectState)
        spinner = findViewById(R.id.spinner)

    }

}