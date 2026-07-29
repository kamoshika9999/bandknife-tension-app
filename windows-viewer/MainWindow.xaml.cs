using System.Globalization;
using System.IO;
using System.Text;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Media;
using ScottPlot;

namespace BandKnifeViewer;

public partial class MainWindow : Window
{
    private const string AllFilterLabel = "すべての設備";

    private readonly List<MeasurementRecord> _records = new();
    private string _dataFolder = "";
    private string? _driveSourceFolder;
    private FileSystemWatcher? _driveWatcher;
    private EquipmentMaster _equipmentMaster = new();
    private List<EquipmentItem> _equipmentItems = new();
    private bool _equipmentDirty;

    public MainWindow()
    {
        InitializeComponent();
        ApplyPlotTheme();
        TryAutoDetectFolder();
        StartBackgroundMirrorSync();
        ReloadData();
    }

    private void TryAutoDetectFolder()
    {
        var envFolder = Environment.GetEnvironmentVariable("BANDKNIFE_DATA_FOLDER");
        if (!string.IsNullOrWhiteSpace(envFolder) && Directory.Exists(envFolder))
        {
            _dataFolder = envFolder;
            FolderText.Text = envFolder;
            _driveSourceFolder = DriveFolderLocator.FindDriveSourceFolder();
            return;
        }

        // 工場 LAN の共有 DATA を既定。到達できないときだけドライブ同期フォルダへフォールバック。
        if (Directory.Exists(SharedFolderPaths.DefaultDataFolder))
        {
            _dataFolder = SharedFolderPaths.DefaultDataFolder;
            FolderText.Text = _dataFolder;
            _driveSourceFolder = DriveFolderLocator.FindDriveSourceFolder();
            return;
        }

        _driveSourceFolder = DriveFolderLocator.FindDriveSourceFolder();
        if (!string.IsNullOrEmpty(_driveSourceFolder))
        {
            _dataFolder = _driveSourceFolder;
            FolderText.Text = _dataFolder;
        }
    }

    /// <summary>起動時にドライブ → 共有フォルダへバックグラウンド同期し、実行中は変更を監視する。</summary>
    private void StartBackgroundMirrorSync()
    {
        _driveSourceFolder ??= DriveFolderLocator.FindDriveSourceFolder();
        var shareFolder = SharedFolderPaths.DefaultDataFolder;
        var driveFolder = _driveSourceFolder;

        Task.Run(() =>
        {
            var report = DataMirrorSync.SyncFromDrive(driveFolder, shareFolder);
            if (report.AnyChange && Directory.Exists(shareFolder) &&
                string.Equals(_dataFolder, shareFolder, StringComparison.OrdinalIgnoreCase))
            {
                Dispatcher.Invoke(() =>
                {
                    if (!ConfirmDiscardEquipmentChanges()) return;
                    ReloadData();
                });
            }
        });

        if (string.IsNullOrEmpty(driveFolder) || !Directory.Exists(driveFolder)) return;

        _driveWatcher = new FileSystemWatcher(driveFolder)
        {
            Filter = "*.*",
            NotifyFilter = NotifyFilters.LastWrite | NotifyFilters.FileName
        };
        _driveWatcher.Changed += OnDriveDataChanged;
        _driveWatcher.Created += OnDriveDataChanged;
        _driveWatcher.EnableRaisingEvents = true;
    }

    private void OnDriveDataChanged(object sender, FileSystemEventArgs e)
    {
        var name = Path.GetFileName(e.FullPath);
        if (name != SharedFolderPaths.RecordsFileName &&
            name != SharedFolderPaths.EquipmentFileName) return;

        var driveFolder = _driveSourceFolder;
        var shareFolder = SharedFolderPaths.DefaultDataFolder;
        if (string.IsNullOrEmpty(driveFolder)) return;

        Task.Run(() =>
        {
            Thread.Sleep(500);
            var report = DataMirrorSync.SyncFromDrive(driveFolder, shareFolder);
            if (!report.AnyChange) return;
            if (!Directory.Exists(shareFolder) ||
                !string.Equals(_dataFolder, shareFolder, StringComparison.OrdinalIgnoreCase)) return;
            Dispatcher.Invoke(() =>
            {
                if (!ConfirmDiscardEquipmentChanges()) return;
                ReloadData();
            });
        });
    }

    protected override void OnClosed(EventArgs e)
    {
        if (_driveWatcher != null)
        {
            _driveWatcher.EnableRaisingEvents = false;
            _driveWatcher.Changed -= OnDriveDataChanged;
            _driveWatcher.Created -= OnDriveDataChanged;
            _driveWatcher.Dispose();
        }
        base.OnClosed(e);
    }

    private void RefreshData()
    {
        if (!ConfirmDiscardEquipmentChanges()) return;
        ReloadData();
    }

    private void ReloadData()
    {
        _records.Clear();
        if (!string.IsNullOrEmpty(_dataFolder))
        {
            var csvPath = Path.Combine(_dataFolder, "records.csv");
            if (File.Exists(csvPath)) LoadCsv(csvPath);
        }

        var equipmentNames = _records.Select(r => r.EquipmentName).Distinct().OrderBy(x => x).ToList();

        var prevChart = EquipmentCombo.SelectedItem as string;
        EquipmentCombo.Items.Clear();
        foreach (var name in equipmentNames) EquipmentCombo.Items.Add(name);
        if (EquipmentCombo.Items.Count > 0)
            EquipmentCombo.SelectedIndex = prevChart != null && equipmentNames.Contains(prevChart)
                ? equipmentNames.IndexOf(prevChart) : 0;

        var prevFilter = FilterCombo.SelectedItem as string;
        FilterCombo.Items.Clear();
        FilterCombo.Items.Add(AllFilterLabel);
        foreach (var name in equipmentNames) FilterCombo.Items.Add(name);
        FilterCombo.SelectedIndex = prevFilter != null && FilterCombo.Items.Contains(prevFilter)
            ? FilterCombo.Items.IndexOf(prevFilter) : 0;

        UpdateDashboard();
        UpdateChart();
        UpdateTable();
        LoadEquipment();
        StatusText.Text = BuildStatusText(equipmentNames);
    }

    private bool ConfirmDiscardEquipmentChanges()
    {
        if (!_equipmentDirty) return true;
        var result = System.Windows.MessageBox.Show(
            "設備マスターに未保存の変更があります。破棄して読み込み直しますか？",
            "未保存の変更",
            MessageBoxButton.YesNo, MessageBoxImage.Warning);
        return result == MessageBoxResult.Yes;
    }

    private string BuildStatusText(List<string> equipmentNames)
    {
        if (_records.Count == 0 && _equipmentItems.Count == 0)
            return "データがありません。「フォルダ選択」で records.csv / equipment.json のあるフォルダを指定してください。";
        var parts = new List<string> { $"最終読み込み: {DateTime.Now:yyyy/MM/dd HH:mm:ss}" };
        if (_records.Count > 0) parts.Add($"記録数: {_records.Count}");
        if (_equipmentItems.Count > 0) parts.Add($"設備数: {_equipmentItems.Count}");
        if (_equipmentDirty) parts.Add("設備マスターに未保存の変更があります");
        return string.Join(" / ", parts);
    }

    // ===== 設備マスター =====

    private void LoadEquipment()
    {
        _equipmentDirty = false;
        if (string.IsNullOrEmpty(_dataFolder))
        {
            _equipmentMaster = new EquipmentMaster();
            _equipmentItems = new List<EquipmentItem>();
        }
        else
        {
            _equipmentMaster = EquipmentStore.Load(_dataFolder);
            _equipmentItems = _equipmentMaster.Equipment.ToList();
        }
        UpdateEquipmentUi();
    }

    private void UpdateEquipmentUi()
    {
        EquipmentGrid.ItemsSource = null;
        EquipmentGrid.ItemsSource = _equipmentItems;
        var count = _equipmentItems.Count;
        EquipmentMetaText.Text = _equipmentMaster.Revision > 0 || count > 0
            ? $"第 {_equipmentMaster.Revision} 版 / 最終更新: {FormatMasterTime(_equipmentMaster.UpdatedAt)} / 更新者: {FormatUpdatedBy(_equipmentMaster.UpdatedBy)} / {count} 件"
            : File.Exists(EquipmentStore.PathFor(_dataFolder))
                ? "設備マスターを読み込みましたが、登録されている設備がありません"
                : "equipment.json が見つかりません。設備を追加して保存すると作成されます";
        EquipmentStatusText.Text = _equipmentDirty
            ? "未保存の変更があります。保存すると全端末に反映されます"
            : count > 0 ? $"{count} 件の設備を表示中" : "";
    }

    private static string FormatMasterTime(long ms)
        => ms > 0 ? DateTimeOffset.FromUnixTimeMilliseconds(ms).LocalDateTime.ToString("yyyy/MM/dd HH:mm") : "—";

    private static string FormatUpdatedBy(string name)
        => string.IsNullOrWhiteSpace(name) ? "—" : name;

    private EquipmentItem? SelectedEquipment()
        => EquipmentGrid.SelectedItem as EquipmentItem;

    private void AddEquipment_Click(object sender, RoutedEventArgs e)
    {
        if (string.IsNullOrEmpty(_dataFolder))
        {
            System.Windows.MessageBox.Show("先にデータフォルダを選択してください。", "設備管理",
                MessageBoxButton.OK, MessageBoxImage.Information);
            return;
        }
        var item = new EquipmentItem
        {
            Uuid = Guid.NewGuid().ToString(),
            Name = "",
            MassPerMeter = 0.844,
            SpanMeters = 1.0,
            StandardTension = 160.0,
            SpecLower = 150.0,
            SpecUpper = 180.0,
            WidthMm = 86.0,
            ThicknessMm = 1.25
        };
        var dlg = new EquipmentEditDialog(item, isNew: true) { Owner = this };
        if (dlg.ShowDialog() != true) return;
        _equipmentItems.Add(dlg.Result);
        _equipmentDirty = true;
        UpdateEquipmentUi();
        StatusText.Text = BuildStatusText(_records.Select(r => r.EquipmentName).Distinct().OrderBy(x => x).ToList());
    }

    private void EditEquipment_Click(object sender, RoutedEventArgs e) => OpenEquipmentEditor(false);

    private void DuplicateEquipment_Click(object sender, RoutedEventArgs e)
    {
        var selected = SelectedEquipment();
        if (selected == null)
        {
            System.Windows.MessageBox.Show("複製する設備を選択してください。", "設備管理",
                MessageBoxButton.OK, MessageBoxImage.Information);
            return;
        }
        var copy = selected.CloneAsNew();
        var dlg = new EquipmentEditDialog(copy, isNew: true) { Owner = this };
        if (dlg.ShowDialog() != true) return;
        _equipmentItems.Add(dlg.Result);
        _equipmentDirty = true;
        UpdateEquipmentUi();
    }

    private void ViewEquipment_Click(object sender, RoutedEventArgs e)
    {
        var selected = SelectedEquipment();
        if (selected == null)
        {
            System.Windows.MessageBox.Show("閲覧する設備を選択してください。", "設備管理",
                MessageBoxButton.OK, MessageBoxImage.Information);
            return;
        }
        ShowEquipmentDetail(selected);
    }

    private void EquipmentGrid_MouseDoubleClick(object sender, System.Windows.Input.MouseButtonEventArgs e)
        => OpenEquipmentEditor(false);

    private void OpenEquipmentEditor(bool isNew)
    {
        var selected = SelectedEquipment();
        if (selected == null)
        {
            System.Windows.MessageBox.Show("編集する設備を選択してください。", "設備管理",
                MessageBoxButton.OK, MessageBoxImage.Information);
            return;
        }
        var dlg = new EquipmentEditDialog(CloneEquipment(selected), isNew) { Owner = this };
        if (dlg.ShowDialog() != true) return;
        var idx = _equipmentItems.IndexOf(selected);
        if (idx >= 0) _equipmentItems[idx] = dlg.Result;
        _equipmentDirty = true;
        UpdateEquipmentUi();
        EquipmentGrid.SelectedItem = dlg.Result;
    }

    private static EquipmentItem CloneEquipment(EquipmentItem src) => new()
    {
        Uuid = src.Uuid,
        Name = src.Name,
        MassPerMeter = src.MassPerMeter,
        SpanMeters = src.SpanMeters,
        StandardTension = src.StandardTension,
        SpecLower = src.SpecLower,
        SpecUpper = src.SpecUpper,
        UseHzMode = src.UseHzMode,
        StandardHz = src.StandardHz,
        SpecHzLower = src.SpecHzLower,
        SpecHzUpper = src.SpecHzUpper,
        WidthMm = src.WidthMm,
        ThicknessMm = src.ThicknessMm,
        Density = src.Density
    };

    private void ShowEquipmentDetail(EquipmentItem eq)
    {
        var hz = eq.UseHzMode
            ? $"標準周波数: {eq.StandardHz:F1} Hz\n規格（Hz）: {eq.SpecHzLower:F1} – {eq.SpecHzUpper:F1} Hz"
            : $"標準張力: {eq.StandardTension:N0} N\n規格（N）: {eq.SpecLower:N0} – {eq.SpecUpper:N0} N";
        System.Windows.MessageBox.Show(
            $"設備名: {eq.Name}\n" +
            $"判定方法: {eq.ModeText}\n" +
            hz + "\n" +
            $"単位質量: {eq.MassPerMeter:F3} kg/m\n" +
            $"スパン長: {eq.SpanMeters:F2} m\n" +
            $"幅: {eq.WidthMm:F1} mm / 板厚: {eq.ThicknessMm:F2} mm\n" +
            $"密度: {eq.Density:N0} kg/m³\n" +
            $"識別子: {eq.Uuid}",
            $"設備の詳細 — {eq.Name}",
            MessageBoxButton.OK, MessageBoxImage.Information);
    }

    private void DeleteEquipment_Click(object sender, RoutedEventArgs e)
    {
        var selected = SelectedEquipment();
        if (selected == null)
        {
            System.Windows.MessageBox.Show("削除する設備を選択してください。", "設備管理",
                MessageBoxButton.OK, MessageBoxImage.Information);
            return;
        }
        var result = System.Windows.MessageBox.Show(
            $"「{selected.Name}」を設備マスターから削除します。\n\n" +
            "保存後、全端末でこの設備は測定できなくなります。測定履歴は残ります。\n\nよろしいですか？",
            "設備の削除",
            MessageBoxButton.YesNo, MessageBoxImage.Warning);
        if (result != MessageBoxResult.Yes) return;
        _equipmentItems.Remove(selected);
        _equipmentDirty = true;
        UpdateEquipmentUi();
    }

    private void SaveEquipment_Click(object sender, RoutedEventArgs e)
    {
        if (string.IsNullOrEmpty(_dataFolder))
        {
            System.Windows.MessageBox.Show("先にデータフォルダを選択してください。", "設備管理",
                MessageBoxButton.OK, MessageBoxImage.Information);
            return;
        }
        var err = EquipmentValidator.ValidateAll(_equipmentItems);
        if (err != null)
        {
            System.Windows.MessageBox.Show(err, "保存できません", MessageBoxButton.OK, MessageBoxImage.Warning);
            return;
        }

        var updatedBy = UpdatedByBox.Text.Trim();
        if (string.IsNullOrWhiteSpace(updatedBy))
        {
            System.Windows.MessageBox.Show("更新者名を入力してください。", "設備管理",
                MessageBoxButton.OK, MessageBoxImage.Information);
            return;
        }

        var removed = _equipmentMaster.Equipment
            .Where(old => !_equipmentItems.Any(n => n.Uuid == old.Uuid))
            .Select(x => x.Name).ToList();
        if (removed.Count > 0)
        {
            var confirm = System.Windows.MessageBox.Show(
                $"{removed.Count} 件の設備が全端末から消えます（{string.Join("、", removed)}）。\n\nよろしいですか？",
                "設備の削除の確認",
                MessageBoxButton.YesNo, MessageBoxImage.Warning);
            if (confirm != MessageBoxResult.Yes) return;
        }

        _equipmentMaster.Revision = _equipmentMaster.Revision + 1;
        _equipmentMaster.UpdatedAt = DateTimeOffset.Now.ToUnixTimeMilliseconds();
        _equipmentMaster.UpdatedBy = updatedBy;
        _equipmentMaster.Equipment = _equipmentItems.ToList();
        try
        {
            EquipmentStore.Save(_dataFolder, _equipmentMaster);
            _equipmentDirty = false;
            UpdateEquipmentUi();
            var names = _records.Select(r => r.EquipmentName).Distinct().OrderBy(x => x).ToList();
            StatusText.Text = BuildStatusText(names);
            System.Windows.MessageBox.Show(
                $"設備マスターを保存しました（第 {_equipmentMaster.Revision} 版）。\n" +
                $"{EquipmentStore.PathFor(_dataFolder)}",
                "保存完了", MessageBoxButton.OK, MessageBoxImage.Information);
        }
        catch (Exception ex)
        {
            System.Windows.MessageBox.Show($"保存に失敗しました:\n{ex.Message}", "エラー",
                MessageBoxButton.OK, MessageBoxImage.Error);
        }
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
                // Android からの true/false と、本ビューアが書き出す OK/NG の双方を受け付ける
                Passed = parts[4].Equals("True", StringComparison.OrdinalIgnoreCase) ||
                         parts[4].Equals("OK", StringComparison.OrdinalIgnoreCase),
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

    // ===== ダッシュボード（最新値カード） =====

    // 色帯には彩度の高い色を、文字には暗い背景でも AA を満たす淡い色を使う
    private static readonly SolidColorBrush OkBrush = new(System.Windows.Media.Color.FromRgb(0x4C, 0xAF, 0x50));
    private static readonly SolidColorBrush NgBrush = new(System.Windows.Media.Color.FromRgb(0xEF, 0x53, 0x50));
    private static readonly SolidColorBrush OkTextBrush = new(System.Windows.Media.Color.FromRgb(0x81, 0xC7, 0x84));
    private static readonly SolidColorBrush NgTextBrush = new(System.Windows.Media.Color.FromRgb(0xEF, 0x9A, 0x9A));
    private static readonly SolidColorBrush TextPrimary = new(System.Windows.Media.Color.FromRgb(0xEC, 0xEF, 0xF4));
    private static readonly SolidColorBrush TextSecondary = new(System.Windows.Media.Color.FromRgb(0xB3, 0xBC, 0xC7));
    private static readonly SolidColorBrush ItemBg = new(System.Windows.Media.Color.FromRgb(0x17, 0x1B, 0x21));

    private void UpdateDashboard()
    {
        DashboardPanel.Children.Clear();
        var latest = _records.GroupBy(r => r.EquipmentName)
            .Select(g => g.OrderByDescending(r => r.Timestamp).First())
            .OrderBy(r => r.EquipmentName).ToList();

        if (latest.Count == 0)
        {
            DashboardPanel.Children.Add(new TextBlock
            {
                Text = "データがありません",
                Foreground = TextSecondary,
                FontSize = 13,
                Margin = new Thickness(2, 8, 0, 0)
            });
            return;
        }

        foreach (var r in latest)
        {
            var statusBrush = r.Passed ? OkBrush : NgBrush;
            var statusTextBrush = r.Passed ? OkTextBrush : NgTextBrush;

            var item = new Border
            {
                Background = ItemBg,
                CornerRadius = new CornerRadius(8),
                Padding = new Thickness(12, 10, 12, 10),
                Margin = new Thickness(0, 0, 0, 8)
            };

            var grid = new Grid();
            grid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(4) });
            grid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(10) });
            grid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
            grid.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });

            var bar = new Border { Background = statusBrush, CornerRadius = new CornerRadius(2) };
            Grid.SetColumn(bar, 0);
            grid.Children.Add(bar);

            var info = new StackPanel();
            info.Children.Add(new TextBlock
            {
                Text = r.EquipmentName,
                Foreground = TextPrimary,
                FontSize = 13,
                FontWeight = FontWeights.SemiBold,
                TextTrimming = TextTrimming.CharacterEllipsis
            });
            info.Children.Add(new TextBlock
            {
                Text = $"{r.Timestamp:yyyy/MM/dd HH:mm} / {r.FrequencyHz:F1} Hz",
                Foreground = TextSecondary,
                FontSize = 13,
                Margin = new Thickness(0, 2, 0, 0)
            });
            Grid.SetColumn(info, 2);
            grid.Children.Add(info);

            var valuePanel = new StackPanel { HorizontalAlignment = System.Windows.HorizontalAlignment.Right };
            valuePanel.Children.Add(new TextBlock
            {
                Text = $"{r.TensionN:N0} N",
                Foreground = TextPrimary,
                FontSize = 18,
                FontWeight = FontWeights.Bold,
                HorizontalAlignment = System.Windows.HorizontalAlignment.Right
            });
            valuePanel.Children.Add(new TextBlock
            {
                Text = r.Passed ? "✓ OK" : "⚠ 要調整",
                Foreground = statusTextBrush,
                FontSize = 13,
                FontWeight = FontWeights.SemiBold,
                HorizontalAlignment = System.Windows.HorizontalAlignment.Right
            });
            Grid.SetColumn(valuePanel, 3);
            grid.Children.Add(valuePanel);

            item.Child = grid;
            DashboardPanel.Children.Add(item);
        }
    }

    // ===== 推移グラフ =====

    private void ApplyPlotTheme()
    {
        var plot = TrendPlot.Plot;
        plot.FigureBackground.Color = ScottPlot.Color.FromHex("#1E232B");
        plot.DataBackground.Color = ScottPlot.Color.FromHex("#171B21");
        plot.Axes.Color(ScottPlot.Color.FromHex("#9AA5B1"));
        plot.Grid.MajorLineColor = ScottPlot.Color.FromHex("#2E3540");
    }

    private void UpdateChart()
    {
        var plot = TrendPlot.Plot;
        plot.Clear();

        var eq = EquipmentCombo.SelectedItem as string;
        var data = eq == null
            ? new List<MeasurementRecord>()
            : _records.Where(r => r.EquipmentName == eq).OrderBy(r => r.Timestamp).ToList();

        if (data.Count > 0)
        {
            var xs = data.Select(r => r.Timestamp.ToOADate()).ToArray();
            var ys = data.Select(r => r.TensionN).ToArray();

            var scatter = plot.Add.Scatter(xs, ys);
            scatter.Color = ScottPlot.Color.FromHex("#42A5F5");
            scatter.LineWidth = 2;
            scatter.MarkerSize = 7;

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
                var trend = plot.Add.Scatter(xs, trendY);
                trend.Color = ScottPlot.Color.FromHex("#FFB74D");
                trend.LineWidth = 1.5f;
                trend.MarkerSize = 0;
                trend.LinePattern = LinePattern.Dashed;
            }

            plot.Axes.Left.Label.Text = "張力 (N)";
            plot.Axes.Left.Label.ForeColor = ScottPlot.Color.FromHex("#9AA5B1");
            plot.Axes.AutoScale();
        }

        plot.Axes.DateTimeTicksBottom();
        TrendPlot.Refresh();
    }

    // ===== 履歴テーブル =====

    private void UpdateTable()
    {
        var filter = FilterCombo.SelectedItem as string;
        var rows = _records.OrderByDescending(r => r.Timestamp).AsEnumerable();
        if (!string.IsNullOrEmpty(filter) && filter != AllFilterLabel)
            rows = rows.Where(r => r.EquipmentName == filter);
        var list = rows.ToList();
        HistoryGrid.ItemsSource = list;
        RecordCountText.Text = $"{list.Count} 件";
    }

    // ===== イベント =====

    private void SelectFolder_Click(object sender, RoutedEventArgs e)
    {
        var dlg = new System.Windows.Forms.FolderBrowserDialog
        {
            Description = "records.csv / equipment.json のあるフォルダを選択してください",
            UseDescriptionForTitle = true
        };
        if (dlg.ShowDialog() == System.Windows.Forms.DialogResult.OK)
        {
            if (!ConfirmDiscardEquipmentChanges()) return;
            _dataFolder = dlg.SelectedPath;
            FolderText.Text = _dataFolder;
            ReloadData();
        }
    }

    private void Refresh_Click(object sender, RoutedEventArgs e) => RefreshData();

    private void EquipmentCombo_SelectionChanged(object sender, SelectionChangedEventArgs e) => UpdateChart();

    private void FilterCombo_SelectionChanged(object sender, SelectionChangedEventArgs e)
    {
        if (IsLoaded || FilterCombo.SelectedItem != null) UpdateTable();
    }

    private void ExportCsv_Click(object sender, RoutedEventArgs e)
    {
        var dlg = new Microsoft.Win32.SaveFileDialog
        {
            Filter = "CSV|*.csv",
            FileName = $"張力記録_{DateTime.Now:yyyyMMdd}.csv"
        };
        if (dlg.ShowDialog() != true) return;
        // 画面で絞り込んだ結果をそのまま書き出す。全件が欲しいときは「すべての設備」を選んでもらう。
        var rows = HistoryGrid.ItemsSource as IEnumerable<MeasurementRecord> ?? _records;
        var list = rows.ToList();
        var sb = new StringBuilder("timestamp,equipmentName,frequencyHz,tensionN,passed,comment\n");
        foreach (var r in list)
            sb.AppendLine($"{new DateTimeOffset(r.Timestamp).ToUnixTimeMilliseconds()},{r.EquipmentName},{r.FrequencyHz},{r.TensionN},{(r.Passed ? "OK" : "NG")},\"{r.Comment}\"");
        File.WriteAllText(dlg.FileName, sb.ToString(), Encoding.UTF8);
        System.Windows.MessageBox.Show(
            $"{list.Count} 件をエクスポートしました:\n{dlg.FileName}",
            "CSV出力", MessageBoxButton.OK, MessageBoxImage.Information);
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

    public string TimestampText => Timestamp.ToString("yyyy/MM/dd HH:mm:ss");
    public string FrequencyText => FrequencyHz.ToString("F1");
    public string TensionText => TensionN.ToString("N0");
}
