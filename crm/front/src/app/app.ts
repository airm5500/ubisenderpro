import { Component, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { ToastModule } from 'primeng/toast';
import { ConfirmDialogModule } from 'primeng/confirmdialog';
import { CharteService } from './core/charte';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, ToastModule, ConfirmDialogModule],
  template: `<p-toast position="top-right" /><p-confirmdialog /><router-outlet />`
})
export class App {
  constructor() {
    inject(CharteService).charger();
  }
}
