class SettingsActivity : AppCompatActivity() {
    private lateinit var settingsManager: SettingsManager
    private lateinit var engineAdapter: EngineAdapter
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        
        settingsManager = SettingsManager.getInstance(this)
        
        setupRecyclerView()
        setupSwitches()
        setupSaveButton()
    }
    
    private fun setupRecyclerView() {
        engineAdapter = EngineAdapter(settingsManager.getEngineOrder().toMutableList())
        findViewById<RecyclerView>(R.id.engine_list).apply {
            layoutManager = LinearLayoutManager(this@SettingsActivity)
            adapter = engineAdapter
            ItemTouchHelper(DragCallback(engineAdapter)).attachToRecyclerView(this)
        }
    }
    
    private fun setupSwitches() {
        findViewById<Switch>(R.id.auto_hide_switch).apply {
            isChecked = settingsManager.getAutoHide()
            setOnCheckedChangeListener { _, isChecked ->
                settingsManager.setAutoHide(isChecked)
            }
        }
    }
    
    private fun setupSaveButton() {
        findViewById<Button>(R.id.save_settings).setOnClickListener {
            settingsManager.saveEngineOrder(engineAdapter.getEngines())
            finish()
        }
    }
} 