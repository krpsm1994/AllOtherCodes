import { ChangeDetectorRef, Component, HostListener, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { DecimalPipe } from '@angular/common';
import { FormsModule, ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { HttpClient, HttpParams } from '@angular/common/http';
import { AuthService } from '../services/auth.service';
import { TradeService, Trade, Instrument, CandleRow } from '../services/trade.service';
import { SettingsService, AlgoSettings, DEFAULT_ALGO_SETTINGS } from '../services/settings.service';
import { NgApexchartsModule } from 'ng-apexcharts';
import type {
  ApexAxisChartSeries, ApexChart, ApexXAxis, ApexYAxis,
  ApexTitleSubtitle, ApexTooltip, ApexPlotOptions, ApexTheme
} from 'ng-apexcharts';

type FilterOption = 'All' | 'Active' | 'Inactive';
type ActiveTab    = 'Trades' | 'Candles' | 'Backtest';

const ACTIVE_STATUSES   = ['WATCHING', 'TRIGGERED', 'OPEN', 'SELL IN PROGRESS'];
const INACTIVE_STATUSES = ['CLOSED', 'NOT TRIGGERED'];

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [DecimalPipe, FormsModule, ReactiveFormsModule, NgApexchartsModule],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.css'
})
export class DashboardComponent implements OnInit, OnDestroy {

  // ── State ─────────────────────────────────────────────────────────
  activeTab: ActiveTab = 'Trades';
  filter: FilterOption = 'All';
  filterOpen = false;

  trades: Trade[]     = [];
  tradesLoading       = false;
  tradesError         = '';

  // ── Candles tab ──────────────────────────────────────────────────
  instruments: Instrument[]       = [];
  instrumentsLoading              = false;
  selectedInstrument: Instrument | null = null;
  instrumentDropOpen              = false;
  instrumentSearch                = '';

  candles: CandleRow[]            = [];
  candlesLoading                  = false;
  candlesError                    = '';
  candlePage                      = 0;
  readonly CANDLES_PER_PAGE       = 50;

  // ── Candle chart ──────────────────────────────────────────────────
  candleView: 'table' | 'chart' = 'table';

  chartSeries: ApexAxisChartSeries = [];
  chartOptions: ApexChart = {
    type: 'candlestick',
    height: 500,
    toolbar: { show: true },
    animations: { enabled: false }
  };
  chartXAxis: ApexXAxis = { type: 'datetime', labels: { datetimeUTC: false } };
  chartYAxis: ApexYAxis = { tooltip: { enabled: true } };
  chartTooltip: ApexTooltip = { enabled: true, theme: 'dark' };
  chartPlotOptions: ApexPlotOptions = {
    candlestick: {
      colors: { upward: '#16a34a', downward: '#dc2626' },
      wick: { useFillColor: true }
    }
  };
  chartTitle: ApexTitleSubtitle = { text: '', align: 'left', style: { fontSize: '14px' } };

  // ── Trade modal ───────────────────────────────────────────────────
  tradeModalOpen  = false;
  tradeModalSaving = false;
  tradeModalError  = '';
  editingTradeId: number | null = null;

  tradeInstrumentSearch = '';
  tradeInstrumentDropOpen = false;
  selectedTradeInstrument: Instrument | null = null;

  tradeForm: FormGroup;

  // ── Broker status ─────────────────────────────────────────────────
  angelDone       = false;
  zerodhaDone     = false;
  zerodhaWaiting  = false;
  private zerodhaPollId?: ReturnType<typeof setInterval>;
  private zerodhaPollStart = 0;
  angelPopupOpen  = false;
  profileOpen     = false;
  settingsOpen    = false;
  settingsSaving  = false;
  settingsError   = '';

  settingsForm: FormGroup;

  // ── Action state ──────────────────────────────────────────────────
  refreshing   = false;
  replacing    = false;
  actionMsg    = '';

  // ── Angel login form ──────────────────────────────────────────────
  angelForm: FormGroup;
  angelError   = '';
  angelLoading = false;

  readonly username: string;

  constructor(
    private router: Router,
    private route: ActivatedRoute,
    private auth: AuthService,
    private tradeService: TradeService,
    private fb: FormBuilder,
    private http: HttpClient,
    private settings: SettingsService,
    private cdr: ChangeDetectorRef
  ) {
    this.username = this.auth.getUsername();
    this.angelDone  = this.auth.isAngelDone();
    this.zerodhaDone = this.auth.isZerodhaDone();
    const d = DEFAULT_ALGO_SETTINGS;
    this.settingsForm = this.fb.group({
      // Algo Scan
      numberOfCandles:        [d.algoScan.numberOfCandles,        [Validators.required, Validators.min(1)]],
      // Orders
      place10MinOrders:       [d.orders.place10MinOrders],
      placeDailyOrders:       [d.orders.placeDailyOrders],
      orderType:              [d.orders.orderType,               Validators.required],
      pollingIntervalMinutes: [d.orders.pollingIntervalMinutes,   [Validators.required, Validators.min(1)]],
      max10MinOrders:         [d.orders.max10MinOrders,           [Validators.required, Validators.min(1)]],
      maxDailyOrders:         [d.orders.maxDailyOrders,           [Validators.required, Validators.min(1)]]
    });

    this.angelForm = this.fb.group({
      clientcode: ['', Validators.required],
      pin:        ['', Validators.required],
      totp:       ['', Validators.required]
    });

    this.tradeForm = this.fb.group({
      token:       ['', Validators.required],
      name:        ['', Validators.required],
      date:        ['', Validators.required],
      buyPrice:    [0,  [Validators.required, Validators.min(0)]],
      sellPrice:   [0,  Validators.required],
      status:      ['WATCHING', Validators.required],
      noOfShares:  [1,  [Validators.required, Validators.min(1)]],
      pnl:         [null],
      buyOrderId:  [''],
      sellOrderId: [''],
      type:        ['10MinWatchlist', Validators.required]
    });
  }

  // initial value is DEFAULT_NUMBER_OF_CANDLES; actual value is loaded on init

  ngOnInit(): void {
    this.loadTrades();
    // Load persisted settings from server
    this.settings.fetchSettings().subscribe(s => this.patchSettingsForm(s));
    const params = this.route.snapshot.queryParamMap;

    // Server-side callback exchanged token and redirected here — poll confirms success
    // (zerodha=error means exchange failed on the server)
    const zerodhaParam = params.get('zerodha');
    if (zerodhaParam === 'error') {
      this.actionMsg = 'Zerodha login failed — please try again.';
      setTimeout(() => this.actionMsg = '', 6000);
      this.router.navigate([], { replaceUrl: true });
    } else if (this.zerodhaWaiting) {
      // Already polling — will pick up the new session automatically
    } else {
      // May have arrived here from a server callback redirect — check status once
      this.auth.zerodhaStatus().subscribe({
        next: s => {
          if (s?.loggedIn) {
            this.auth.saveZerodhaSession();
            this.zerodhaDone = true;
          }
        }
      });
    }
    // Clean any query params from the URL without reloading
    if (params.keys.length > 0) {
      this.router.navigate([], { replaceUrl: true });
    }
  }

  ngOnDestroy(): void {
    this.stopZerodhaPoll();
  }

  // ── Tabs ──────────────────────────────────────────────────────────
  setTab(tab: ActiveTab): void {
    this.activeTab = tab;
    if (tab === 'Candles' && this.instruments.length === 0) {
      this.loadInstruments();
    }
  }

  // ── Filter ────────────────────────────────────────────────────────
  get filteredTrades(): Trade[] {
    if (this.filter === 'Active') {
      return this.trades.filter(t => ACTIVE_STATUSES.includes(t.status?.toUpperCase() ?? ''));
    }
    if (this.filter === 'Inactive') {
      return this.trades.filter(t => INACTIVE_STATUSES.includes(t.status?.toUpperCase() ?? ''));
    }
    return this.trades;
  }

  setFilter(f: FilterOption): void {
    this.filter = f;
    this.filterOpen = false;
  }

  @HostListener('document:click')
  onDocClick(): void {
    this.filterOpen = false;
    this.profileOpen = false;
    this.instrumentDropOpen = false;
    this.tradeInstrumentDropOpen = false;
  }

  stopProp(e: Event): void { e.stopPropagation(); }

  // ── Trades ────────────────────────────────────────────────────────
  loadTrades(): void {
    this.tradesLoading = true;
    this.tradesError   = '';
    this.tradeService.getTrades().subscribe({
      next: data => { this.trades = data; this.tradesLoading = false; this.cdr.detectChanges(); },
      error: err => {
        this.tradesError  = err?.error?.message || 'Failed to load trades.';
        this.tradesLoading = false;
        this.cdr.detectChanges();
      }
    });
  }

  // ── Trade modal ───────────────────────────────────────────────────
  get filteredTradeInstruments(): Instrument[] {
    const q = this.tradeInstrumentSearch.trim().toLowerCase();
    if (!q) return this.instruments;
    return this.instruments.filter(i => i.name.toLowerCase().includes(q));
  }

  selectTradeInstrument(inst: Instrument): void {
    this.selectedTradeInstrument = inst;
    this.tradeInstrumentDropOpen = false;
    this.tradeInstrumentSearch   = '';
    this.tradeForm.patchValue({ token: inst.token, name: inst.name, type: inst.type });
  }

  openAddTrade(): void {
    if (this.instruments.length === 0) this.loadInstruments();
    this.editingTradeId          = null;
    this.selectedTradeInstrument = null;
    this.tradeInstrumentSearch   = '';
    this.tradeModalError         = '';
    const today = new Date().toISOString().slice(0, 10);
    this.tradeForm.reset({
      token: '', name: '', date: today,
      buyPrice: 0, sellPrice: 0, status: 'WATCHING',
      noOfShares: 1, pnl: null, buyOrderId: '', sellOrderId: '',
      type: '10MinWatchlist'
    });
    this.tradeModalOpen = true;
  }

  openEditTrade(t: Trade, e: Event): void {
    e.stopPropagation();
    if (this.instruments.length === 0) this.loadInstruments();
    this.editingTradeId          = t.id ?? null;
    this.selectedTradeInstrument = null;
    this.tradeInstrumentSearch   = '';
    this.tradeModalError         = '';
    this.tradeForm.reset({
      token:       t.token,
      name:        t.name,
      date:        t.date,
      buyPrice:    t.buyPrice,
      sellPrice:   t.sellPrice,
      status:      t.status,
      noOfShares:  t.noOfShares,
      pnl:         t.pnl,
      buyOrderId:  t.buyOrderId  || '',
      sellOrderId: t.sellOrderId || '',
      type:        t.type        || '10MinWatchlist'
    });
    this.tradeModalOpen = true;
  }

  closeTradeModal(): void {
    this.tradeModalOpen          = false;
    this.tradeModalError         = '';
    this.tradeInstrumentDropOpen = false;
  }

  deleteTrade(t: Trade, e: Event): void {
    e.stopPropagation();
    if (t.id == null) return;
    if (!confirm(`Delete trade "${t.name}" (${t.date})?`)) return;
    this.tradeService.deleteTrade(t.id).subscribe({
      next: () => {
        this.loadTrades();
        this.actionMsg = 'Trade deleted.';
        setTimeout(() => this.actionMsg = '', 3000);
      },
      error: err => {
        this.actionMsg = err?.error?.message || 'Failed to delete trade.';
        setTimeout(() => this.actionMsg = '', 4000);
      }
    });
  }

  submitTradeModal(): void {
    if (this.tradeForm.invalid) { this.tradeForm.markAllAsTouched(); return; }
    const f = this.tradeForm.value;
    const payload: Trade = {
      token:       f.token,
      name:        f.name,
      date:        f.date || new Date().toISOString().slice(0, 10),
      buyPrice:    Number(f.buyPrice),
      sellPrice:   Number(f.sellPrice),
      status:      f.status,
      noOfShares:  Number(f.noOfShares),
      pnl:         f.pnl != null ? Number(f.pnl) : null,
      buyOrderId:  f.buyOrderId  || null,
      sellOrderId: f.sellOrderId || null,
      type:        f.type
    };
    this.tradeModalSaving = true;
    this.tradeModalError  = '';
    const op = this.editingTradeId != null
      ? this.tradeService.updateTrade(this.editingTradeId, payload)
      : this.tradeService.createTrade(payload);
    op.subscribe({
      next: () => {
        this.tradeModalSaving = false;
        this.closeTradeModal();
        this.loadTrades();
        this.actionMsg = this.editingTradeId != null ? 'Trade updated.' : 'Trade added.';
        setTimeout(() => this.actionMsg = '', 3000);
      },
      error: err => {
        this.tradeModalSaving = false;
        this.tradeModalError  = err?.error?.message || 'Failed to save trade.';
      }
    });
  }

  // ── Instruments (candles tab) ─────────────────────────────────────
  loadInstruments(): void {
    this.instrumentsLoading = true;
    this.tradeService.getInstruments().subscribe({
      next: data => { this.instruments = data; this.instrumentsLoading = false; this.cdr.detectChanges(); },
      error: ()  => { this.instrumentsLoading = false; this.cdr.detectChanges(); }
    });
  }

  get filteredInstruments(): Instrument[] {
    const q = this.instrumentSearch.trim().toLowerCase();
    if (!q) return this.instruments;
    return this.instruments.filter(i => i.name.toLowerCase().includes(q));
  }

  selectInstrument(inst: Instrument): void {
    this.selectedInstrument = inst;
    this.instrumentDropOpen = false;
    this.instrumentSearch   = '';
    this.candles            = [];
    this.candlesError       = '';
    this.candlePage         = 0;
  }

  fetchSelectedCandles(): void {
    if (!this.selectedInstrument) return;
    const inst = this.selectedInstrument;

    if (inst.type === '10MinWatchlist') {
      this.candlesLoading = true;
      this.candlesError   = '';
      this.candlePage     = 0;
      this.tradeService.getCandlesByToken(inst.token).subscribe({
        next: data => {
          this.candles = data.slice().reverse();
          this.candlesLoading = false;
          this.buildCandleChart();
        },
        error: err => {
          this.candlesError   = err?.error?.message || 'Failed to fetch candles.';
          this.candlesLoading = false;
        }
      });
    } else {
      const clientcode = this.auth.getAngelClientcode();
      if (!clientcode) {
        this.candlesError = 'Please login to AngelOne first.';
        return;
      }
      this.candlesLoading = true;
      this.candlesError   = '';
      this.candlePage     = 0;
      this.tradeService.getDailyCandles(inst.token, clientcode).subscribe({
        next: data => {
          this.candles = data.slice().reverse();
          this.candlesLoading = false;
          this.buildCandleChart();
        },
        error: err => {
          this.candlesError   = err?.error?.message || 'Failed to fetch daily candles.';
          this.candlesLoading = false;
        }
      });
    }
  }

  private buildCandleChart(): void {
    this.chartTitle = {
      text: this.selectedInstrument?.name ?? '',
      align: 'left',
      style: { fontSize: '14px', fontWeight: '600', color: '#111827' }
    };
    // ApexCharts candlestick series expects { x: timestamp_ms, y: [o, h, l, c] }
    // candles are already newest-first after .reverse() — sort oldest-first for chart
    const sorted = [...this.candles].reverse();
    this.chartSeries = [{
      name: 'Price',
      data: sorted.map(c => ({
        x: new Date(c.date).getTime(),
        y: [c.open, c.high, c.low, c.close]
      }))
    }];
  }

  setCandleView(v: 'table' | 'chart'): void {
    this.candleView = v;
  }

  get candlePagedData(): CandleRow[] {
    const start = this.candlePage * this.CANDLES_PER_PAGE;
    return this.candles.slice(start, start + this.CANDLES_PER_PAGE);
  }

  get candleTotalPages(): number {
    return Math.max(1, Math.ceil(this.candles.length / this.CANDLES_PER_PAGE));
  }

  // ── Refresh tokens ────────────────────────────────────────────────
  onRefreshTokens(): void {
    this.refreshing = true;
    this.actionMsg  = '';
    this.tradeService.refreshTokens().subscribe({
      next: res => {
        this.refreshing = false;
        this.actionMsg  = `Tokens refreshed — ${res.saved ?? 0} instruments saved.`;
        setTimeout(() => this.actionMsg = '', 4000);
      },
      error: err => {
        this.refreshing = false;
        this.actionMsg  = err?.error?.message || 'Refresh tokens failed.';
        setTimeout(() => this.actionMsg = '', 4000);
      }
    });
  }

  // ── Replace candles ───────────────────────────────────────────────
  onReplaceCandles(): void {
    const clientcode = this.auth.getAngelClientcode();
    if (!clientcode) {
      this.actionMsg = 'Please login to AngelOne first.';
      setTimeout(() => this.actionMsg = '', 4000);
      return;
    }
    this.replacing = true;
    this.actionMsg  = '';
    this.tradeService.replaceCandles(clientcode).subscribe({
      next: res => {
        this.replacing = false;
        this.actionMsg = `Candles replaced — ${res.saved ?? 0} rows saved.`;
        setTimeout(() => this.actionMsg = '', 4000);
      },
      error: err => {
        this.replacing = false;
        this.actionMsg = err?.error?.message || 'Replace candles failed.';
        setTimeout(() => this.actionMsg = '', 4000);
      }
    });
  }

  // ── AngelOne popup ────────────────────────────────────────────────
  openAngelPopup(): void  { this.angelPopupOpen = true; this.angelError = ''; }
  closeAngelPopup(): void { this.angelPopupOpen = false; this.angelForm.reset(); }

  submitAngel(): void {
    if (this.angelForm.invalid) { this.angelForm.markAllAsTouched(); return; }
    this.angelLoading = true;
    this.angelError   = '';

    this.auth.angelLogin(this.angelForm.value).subscribe({
      next: res => {
        this.angelLoading = false;
        if (res?.status) {
          this.auth.saveAngelSession(this.angelForm.value.clientcode);
          this.angelDone = true;
          this.closeAngelPopup();
          this.actionMsg = 'AngelOne login successful.';
          setTimeout(() => this.actionMsg = '', 4000);
        } else {
          this.angelError = res?.message || 'AngelOne login failed.';
        }
      },
      error: err => {
        this.angelLoading = false;
        this.angelError = err?.error?.message || 'AngelOne login failed.';
      }
    });
  }

  reloginAngel(): void { this.openAngelPopup(); }

  // ── Zerodha ───────────────────────────────────────────────────────
  openZerodhaLogin(): void {
    this.auth.zerodhaLoginUrl().subscribe({
      next: res => {
        if (res?.url) {
          this.zerodhaWaiting  = true;
          this.zerodhaPollStart = Date.now();
          window.open(res.url, '_blank');
          // Poll /api/broker/zerodha/status until login completes in the other tab
          this.zerodhaPollId = setInterval(() => {
            if (Date.now() - this.zerodhaPollStart > 5 * 60_000) {
              this.stopZerodhaPoll(); // timeout after 5 min
              this.zerodhaWaiting = false;
              return;
            }
            this.auth.zerodhaStatus().subscribe({
              next: s => {
                if (s?.loggedIn && s.loginTimeMs > this.zerodhaPollStart) {
                  this.stopZerodhaPoll();
                  this.auth.saveZerodhaSession();
                  this.zerodhaDone    = true;
                  this.zerodhaWaiting = false;
                  this.actionMsg = 'Zerodha login successful.';
                  setTimeout(() => this.actionMsg = '', 4000);
                }
              }
            });
          }, 3000);
        }
      },
      error: () => {
        this.actionMsg = 'Failed to get Zerodha login URL.';
        setTimeout(() => this.actionMsg = '', 4000);
      }
    });
  }

  private stopZerodhaPoll(): void {
    if (this.zerodhaPollId) { clearInterval(this.zerodhaPollId); this.zerodhaPollId = undefined; }
  }

  // ── Profile ───────────────────────────────────────────────────────
  logout(): void {
    this.auth.logout();
    this.router.navigate(['/login']);
  }

  openSettings(): void {
    this.settingsError = '';
    // Patch from cache first (instant), then refresh from server
    this.patchSettingsForm(this.settings.getCachedSettings());
    this.settings.fetchSettings().subscribe(s => this.patchSettingsForm(s));
    this.settingsOpen = true;
    this.profileOpen  = false;
  }

  closeSettings(): void {
    this.settingsOpen  = false;
    this.settingsError = '';
  }

  submitSettings(): void {
    if (this.settingsForm.invalid) { this.settingsForm.markAllAsTouched(); return; }
    const f = this.settingsForm.value;
    const payload: AlgoSettings = {
      algoScan: {
        numberOfCandles: Number(f.numberOfCandles)
      },
      orders: {
        place10MinOrders:       !!f.place10MinOrders,
        placeDailyOrders:       !!f.placeDailyOrders,
        orderType:              f.orderType as 'MARKET' | 'LIMIT',
        pollingIntervalMinutes: Number(f.pollingIntervalMinutes),
        max10MinOrders:         Number(f.max10MinOrders),
        maxDailyOrders:         Number(f.maxDailyOrders)
      }
    };
    this.settingsSaving = true;
    this.settingsError  = '';
    this.settings.saveSettings(payload).subscribe(ok => {
      this.settingsSaving = false;
      if (ok) {
        this.settingsOpen = false;
        this.actionMsg    = 'Settings saved.';
        setTimeout(() => this.actionMsg = '', 3000);
      } else {
        this.settingsError = 'Failed to save settings. Please try again.';
      }
    });
  }

  private patchSettingsForm(s: AlgoSettings): void {
    this.settingsForm.patchValue({
      numberOfCandles:        s.algoScan.numberOfCandles,
      place10MinOrders:       s.orders.place10MinOrders,
      placeDailyOrders:       s.orders.placeDailyOrders,
      orderType:              s.orders.orderType,
      pollingIntervalMinutes: s.orders.pollingIntervalMinutes,
      max10MinOrders:         s.orders.max10MinOrders,
      maxDailyOrders:         s.orders.maxDailyOrders
    });
  }

  trackTrade(t: Trade): string | number {
    return t.id ?? (t.token + t.date);
  }

  get userInitial(): string {
    return (this.username?.[0] ?? 'U').toUpperCase();
  }

  // ── PnL class ─────────────────────────────────────────────────────
  pnlClass(pnl: number | null): string {
    if (pnl == null) return '';
    return pnl > 0 ? 'pos' : pnl < 0 ? 'neg' : '';
  }

  // ── Status class ──────────────────────────────────────────────────
  statusClass(status: string): string {
    switch (status?.toUpperCase()) {
      case 'OPEN':               return 'badge badge--open';
      case 'WATCHING':           return 'badge badge--watch';
      case 'TRIGGERED':          return 'badge badge--trigger';
      case 'SELL IN PROGRESS':   return 'badge badge--sell';
      case 'CLOSED':             return 'badge badge--closed';
      case 'NOT TRIGGERED':      return 'badge badge--none';
      default:                   return 'badge';
    }
  }
}
