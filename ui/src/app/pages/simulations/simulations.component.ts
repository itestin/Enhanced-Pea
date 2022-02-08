import {Component, OnInit} from '@angular/core'
import {NzMessageService, NzModalService, simpleEmptyImage} from 'ng-zorro-antd'
import {HomeService, WorkerData} from 'src/app/api/home.service'
import {MemberStatus, SimulationModel} from 'src/app/model/pea.model'
import {ResourceService} from "../../api/resource.service";

@Component({
  selector: 'app-simulations',
  templateUrl: './simulations.component.html',
  styleUrls: ['./simulations.component.css']
})
export class SimulationsComponent implements OnInit {

  workersAllChecked = false
  compilePull = false
  indeterminate = true

  lastCompileTime = ''
  editorBaseUrl = ''
  simulations: SimulationModel[] = []
  workers: SelectWorkerData[] = []
  compilers: SelectWorkerData[] = []

  editVisible = false
  editFile = "edit file"
  editContent = ""

  constructor(
    private homeService: HomeService,
    private modalService: NzModalService,
    private messageService: NzMessageService,
    private resourceService: ResourceService,
  ) {
  }

  handleEditOk() {
    this.resourceService.modifyFile(this.editFile,  this.editContent).subscribe(res => {
      this.editFile = ''
      this.editVisible = false
      this.editContent = ""
    })
  }

  handleEditCancel() {
    this.editFile = ''
    this.editVisible = false
    this.editContent = ""
  }

  edit(simulation: SimulationModel) {
    // 打开浏览器修改的
    // if (this.editorBaseUrl) {
    //   const url = `${this.editorBaseUrl}${simulation.name.replace(/\./g, '/')}.scala`
    //   window.open(url)
    // }

    // 直接页面上修改
    this.resourceService.readFile(`${simulation.name}`).subscribe(res => {
      this.editContent = res.data
      this.editVisible = true
      this.editFile = simulation.name
    })
  }

  compile() {
    const workers = this.workers.filter(item => item.checked)
    if (workers.length > 0) {
      this.homeService.compile(workers.map(item => item.member), this.compilePull).subscribe(res => {
        if (res.data.result) {
          this.compilers = workers
        } else {
          this.modalService.create({
            nzTitle: 'Fail',
            nzContent: JSON.stringify(res.data.errors),
            nzClosable: true,
            nzOkDisabled: true,
          })
        }
      })
    } else {
      this.messageService.error('没有选择节点')
    }
  }

  updateAllChecked() {
    this.indeterminate = false
    if (this.workersAllChecked) {
      this.workers = this.workers.map(item => {
        return {...item, checked: true}
      })
    } else {
      this.workers = this.workers.map(item => {
        return {...item, checked: false}
      })
    }
  }

  updateSingleChecked() {
    if (this.workers.every(item => item.checked === false)) {
      this.workersAllChecked = false
      this.indeterminate = false
    } else if (this.workers.every(item => item.checked === true)) {
      this.workersAllChecked = true
      this.indeterminate = false
    } else {
      this.indeterminate = true
    }
  }

  loadSimulationsData() {
    this.homeService.getSimulations().subscribe(res => {
      this.lastCompileTime = new Date(res.data.last).toLocaleString()
      this.editorBaseUrl = res.data.editorBaseUrl
      // todo 自己模拟 const sm:SimulationModel={name:"SimulationApp",protocols:["p1","p2"],description:"自定义"}
      // var arrType:Array<SimulationModel>=[sm]
      // this.simulations = arrType
      this.simulations = res.data.simulations
    })
  }

  statusColor(item: SelectWorkerData) {
    if (MemberStatus.IDLE === item.status.status) {
      return 'lightseagreen'
    } else {
      return 'lightcoral'
    }
  }

  loadWorkersData() {
    this.homeService.getWorkers().subscribe(res => {
      this.workers = res.data
    })
  }

  ngOnInit(): void {
    this.loadSimulationsData()
    this.loadWorkersData()
  }
}

interface SelectWorkerData extends WorkerData {
  checked?: boolean
}
