using System.Globalization;
using System.IO;
using System.Text;
using System.Windows;
using System.Windows.Controls;
using ScottPlot;

namespace BandKnifeViewer;

public partial class MainWindow : Window
{
    private readonly List<MeasurementRecord> _records = new();
    private string _dataFolder = "";

    public MainWindow()
    {
        InitializeComponent();
        TryAutoDetectFolder();
        RefreshData();
    }

    private void TryAutoDetectFolder()
    {
        var candidates = new[]
        {
            Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.UserProfile), "マイドライブ", "バンドナイフ張力測定"),
            Path.Combine("G:", "マイドライブ", "バンドナイフ張力測定"),
            Path.Combine("D:", "マイドライブ", "バンドナイフ張力測定")
        };
        foreach (var c in candidates)
        {
            if (Directory.Exists(c)) { _dataFolder = c; FolderText.Text = c; return; }
        }
    }

    private void RefreshData()
    {
        _records.Clear();
        if (string.IsNullOrEmpty(_dataFolder)) return;
        var csvPath = Path.Combine(_dataFolder, "records.csv");
        if (File.Exists(csvPath)) LoadCsv(csvPath);
        EquipmentCombo.Items.Clear();
        foreach (var name in _records.Select(r => r.EquipmentName).Distinct().OrderBy(x => x))
            EquipmentCombo.Items.Add(name);
        if (EquipmentCombo.Items.Count > 0) EquipmentCombo.SelectedIndex = 0;
        UpdateDashboard();
    }

    private void LoadCsv(string path)
    {
        var lines = File.ReadAllLines(path, Encoding.UTF8);
        for (int i = 1; i < lines.Length; i++)
        {
            var parts = ParseCsvLine(lines[i]);
            if (parts.Length < 5) continue;
            if (!long.TryParse(parts[0], out var ts)) continue;
            if (!double.TryParse(parts[2], NumberStyles.Float, CultureInfo.InvariantCulture, out var freq)) continue;
            if (!double.TryParse(parts[3], NumberStyles.Float, CultureInfo.InvariantCulture, out var tension)) continue;
            _records.Add(new MeasurementRecord
            {
                Timestamp = DateTimeOffset.FromUnixTimeMilliseconds(ts).LocalDateTime,
                EquipmentName = parts[1],
                FrequencyHz = freq,
                TensionN = tension,
                Passed = parts[4].Equals("True", StringComparison.OrdinalIgnoreCase) || parts[4] == "OK",
                Comment = parts.Length > 9 ? parts[9].Trim('"') : ""
            });
        }
    }

    private static string[] ParseCsvLine(string line)
    {
        var result = new List<string>();
        var current = new StringBuilder();
        var inQuotes = false;
        foreach (var c in line)
        {
            if (c == '"') inQuotes = !inQuotes;
            else if (c == ',' && !inQuotes) { result.Add(current.ToString()); current.Clear(); }
            else current.Append(c);
        }
        result.Add(current.ToString());
        return result.ToArray();
    }

    private void UpdateDashboard()
    {
        DashboardGrid.Children.Clear();
        DashboardGrid.RowDefinitions.Clear();
        var latest = _records.GroupBy(r => r.EquipmentName)
            .Select(g => g.OrderByDescending(r => r.Timestamp).First()).ToList();
        int row = 0;
        foreach (var r in latest)
        {
            DashboardGrid.RowDefinitions.Add(new RowDefinition { Height = GridLength.Auto });
            var panel = new StackPanel { Orientation = System.Windows.Controls.Orientation.Horizontal, Margin = new Thickness(0, 4, 0, 4) };
            panel.Children.Add(new TextBlock { Text = r.EquipmentName, Width = 200, FontWeight = FontWeights.Bold });
            panel.Children.Add(new TextBlock { Text = $"{r.TensionN:F0} N", Width = 100, FontSize = 18 });
            panel.Children.Add(new TextBlock
            {
                Text = r.Passed ? "OK" : "要調整",
                Foreground = r.Passed ? System.Windows.Media.Brushes.Green : System.Windows.Media.Brushes.Red,
                Width = 80, FontWeight = FontWeights.Bold
            });
            Grid.SetRow(panel, row++);
            DashboardGrid.Children.Add(panel);
        }
        UpdateChart();
        UpdateTable();
    }

    private void UpdateChart()
    {
        var eq = EquipmentCombo.SelectedItem as string;
        if (eq == null) return;
        var data = _records.Where(r => r.EquipmentName == eq).OrderBy(r => r.Timestamp).ToList();
        TrendPlot.Plot.Clear();
        if (data.Count > 0)
        {
            var xs = data.Select(r => r.Timestamp.ToOADate()).ToArray();
            var ys = data.Select(r => r.TensionN).ToArray();
            TrendPlot.Plot.Add.Scatter(xs, ys);
            if (data.Count >= 2)
            {
                var n = data.Count;
                var sumX = xs.Sum();
                var sumY = ys.Sum();
                var sumXY = data.Select((r, i) => xs[i] * ys[i]).Sum();
                var sumX2 = xs.Select(x => x * x).Sum();
                var slope = (n * sumXY - sumX * sumY) / (n * sumX2 - sumX * sumX);
                var intercept = (sumY - slope * sumX) / n;
                var trendY = xs.Select(x => slope * x + intercept).ToArray();
                var trend = TrendPlot.Plot.Add.Scatter(xs, trendY);
                trend.Color = ScottPlot.Color.FromHex("#FF9800");
            }
        }
        TrendPlot.Plot.Axes.DateTimeTicksBottom();
        TrendPlot.Refresh();
    }

    private void UpdateTable()
    {
        HistoryGrid.ItemsSource = _records.OrderByDescending(r => r.Timestamp).ToList();
    }

    private void SelectFolder_Click(object sender, RoutedEventArgs e)
    {
        var dlg = new System.Windows.Forms.FolderBrowserDialog();
        if (dlg.ShowDialog() == System.Windows.Forms.DialogResult.OK)
        {
            _dataFolder = dlg.SelectedPath;
            FolderText.Text = _dataFolder;
            RefreshData();
        }
    }

    private void Refresh_Click(object sender, RoutedEventArgs e) => RefreshData();

    private void EquipmentCombo_SelectionChanged(object sender, SelectionChangedEventArgs e) => UpdateChart();

    private void ExportCsv_Click(object sender, RoutedEventArgs e)
    {
        var dlg = new Microsoft.Win32.SaveFileDialog { Filter = "CSV|*.csv", FileName = "export.csv" };
        if (dlg.ShowDialog() != true) return;
        var sb = new StringBuilder("timestamp,equipmentName,frequencyHz,tensionN,passed,comment\n");
        foreach (var r in _records)
            sb.AppendLine($"{new DateTimeOffset(r.Timestamp).ToUnixTimeMilliseconds()},{r.EquipmentName},{r.FrequencyHz},{r.TensionN},{r.Passed},\"{r.Comment}\"");
        File.WriteAllText(dlg.FileName, sb.ToString(), Encoding.UTF8);
        System.Windows.MessageBox.Show("エクスポート完了");
    }
}

public class MeasurementRecord
{
    public DateTime Timestamp { get; set; }
    public string EquipmentName { get; set; } = "";
    public double FrequencyHz { get; set; }
    public double TensionN { get; set; }
    public bool Passed { get; set; }
    public string Comment { get; set; } = "";
}
