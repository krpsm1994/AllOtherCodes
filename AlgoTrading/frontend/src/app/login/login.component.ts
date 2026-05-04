import { Component } from '@angular/core';
import { FormBuilder, FormGroup, Validators, ReactiveFormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../services/auth.service';
import { SettingsService } from '../services/settings.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [ReactiveFormsModule],
  templateUrl: './login.component.html',
  styleUrl: './login.component.css'
})
export class LoginComponent {

  form: FormGroup;
  error = '';
  loading = false;

  constructor(
    private fb: FormBuilder,
    private auth: AuthService,
    private settings: SettingsService,
    private router: Router
  ) {
    this.form = this.fb.group({
      username: ['', [Validators.required, Validators.minLength(3)]],
      password: ['', [Validators.required, Validators.minLength(4)]]
    });

    if (this.auth.isLoggedIn()) {
      this.router.navigate(['/dashboard']);
    }
  }

  get u() { return this.form.get('username')!; }
  get p() { return this.form.get('password')!; }

  onSubmit(): void {
    if (this.form.invalid) { this.form.markAllAsTouched(); return; }
    this.loading = true;
    this.error   = '';

    const { username, password } = this.form.value;

    this.auth.login(username, password).subscribe({
      next: res => {
        this.loading = false;
        if (res.success && res.token) {
          this.auth.saveSession(username, res.token);
          // Load settings from server into cache, then navigate
          this.settings.fetchAllSettingsFromServer().subscribe({ next: () => this.router.navigate(['/dashboard']), error: () => this.router.navigate(['/dashboard']) });
        } else {
          this.error = res.message || 'Login failed.';
        }
      },
      error: err => {
        this.loading = false;
        this.error = err?.error?.message || 'Invalid username or password.';
      }
    });
  }
}
