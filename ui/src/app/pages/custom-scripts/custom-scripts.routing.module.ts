import { NgModule } from '@angular/core'
import { RouterModule, Routes } from '@angular/router'

import { CustomScriptsComponent } from './custom-scripts.component'

const routes: Routes = [
  { path: '', component: CustomScriptsComponent },
]

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule]
})
export class CustomScriptsRoutingModule { }
