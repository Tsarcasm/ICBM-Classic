package icbm.gangshao.platform;

import icbm.gangshao.IAmmunition;
import icbm.gangshao.ProjectileTypes;
import icbm.gangshao.ZhuYaoGangShao;
import icbm.gangshao.damage.IHealthTile;
import icbm.gangshao.terminal.TileEntityTerminal;
import icbm.gangshao.turret.ItemAmmo.AmmoType;
import icbm.gangshao.turret.TPaoDaiBase;

import java.util.HashMap;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.ForgeDirection;
import universalelectricity.core.UniversalElectricity;
import universalelectricity.core.electricity.ElectricityPack;
import universalelectricity.core.vector.Vector3;
import universalelectricity.prefab.CustomDamageSource;

/**
 * Turret Platform
 * 
 * @author Calclavia
 * 
 */
public class TPaoDaiZhan extends TileEntityTerminal implements IInventory
{
	/** The turret linked to this platform. */
	private TPaoDaiBase turret = null;
	/** Deploy direction of the sentry */
	public ForgeDirection deployDirection = ForgeDirection.UP;
	/** CURRENT MAP OF UPGRADES STATS */
	public HashMap<String, Float> upgrades = new HashMap<String, Float>();
	/** The start index of the upgrade slots for the turret. */
	public static final int UPGRADE_START_INDEX = 12;

	/** The first 12 slots are for ammunition. The last 4 slots are for upgrades. */
	public ItemStack[] containingItems = new ItemStack[UPGRADE_START_INDEX + 4];

	@Override
	public void onReceive(ElectricityPack electricityPack)
	{
		/** Creates an explosion if the voltage is too high. */
		if (UniversalElectricity.isVoltageSensitive)
		{
			if (electricityPack.voltage > this.getVoltage())
			{
				TPaoDaiBase turret = this.getTurret(false);

				if (turret != null && turret instanceof IHealthTile)
				{
					((IHealthTile) this.turret).onDamageTaken(CustomDamageSource.electrocution, Integer.MAX_VALUE);
				}

				return;
			}
		}

		this.wattsReceived = Math.min(this.wattsReceived + electricityPack.getWatts(), this.getWattBuffer());

		if ((this.prevWatts <= this.getRequest().getWatts() && this.wattsReceived >= this.getRequest().getWatts()) && !(this.prevWatts == this.wattsReceived))
		{
			this.worldObj.markBlockForUpdate(this.xCoord, this.yCoord, this.zCoord);
		}
	}

	@Override
	public ElectricityPack getRequest()
	{
		if (this.getTurret(false) != null)
		{
			if (this.wattsReceived < this.getTurret(false).getFiringRequest())
			{
				return new ElectricityPack(Math.max(turret.getFiringRequest() / this.getTurret(false).getVoltage(), 0), this.getTurret(false).getVoltage());
			}
		}

		return new ElectricityPack();
	}

	@Override
	public double getWattBuffer()
	{
		if (this.getTurret(false) != null)
		{
			return new ElectricityPack(Math.max(turret.getFiringRequest() / this.getTurret(false).getVoltage(), 0), this.getTurret(false).getVoltage()).getWatts() * 2;
		}

		return 0;
	};

	/** Gets the turret instance linked to this platform */
	public TPaoDaiBase getTurret(boolean getNew)
	{
		Vector3 position = new Vector3(this);

		if (getNew || this.turret == null || this.turret.isInvalid() || !(new Vector3(this.turret).equals(position.clone().modifyPositionFromSide(this.deployDirection))))
		{
			TileEntity tileEntity = position.clone().modifyPositionFromSide(this.deployDirection).getTileEntity(this.worldObj);

			if (tileEntity instanceof TPaoDaiBase)
			{
				this.turret = (TPaoDaiBase) tileEntity;
			}
			else
			{
				this.turret = null;
			}
		}
		return this.turret;
	}

	/**
	 * if a sentry is spawned above the stand it is removed
	 * 
	 * @return
	 */
	public boolean destroyTurret()
	{
		TileEntity ent = this.worldObj.getBlockTileEntity(this.xCoord + deployDirection.offsetX, this.yCoord + deployDirection.offsetY, this.zCoord + deployDirection.offsetZ);

		if (ent instanceof TPaoDaiBase)
		{
			this.turret = null;
			return ((TPaoDaiBase) ent).destroy(false);
		}

		return false;
	}

	public boolean destroy(boolean doExplosion)
	{
		if (doExplosion)
		{
			this.worldObj.createExplosion(null, this.xCoord, this.yCoord, this.zCoord, 2f, true);
		}

		if (!this.worldObj.isRemote)
		{
			this.getBlockType().dropBlockAsItem(this.worldObj, this.xCoord, this.yCoord, this.zCoord, this.getBlockMetadata(), 0);
		}

		return this.worldObj.setBlock(this.xCoord, this.yCoord, this.zCoord, 0);
	}

	@Override
	public String getInvName()
	{
		return this.getBlockType().getLocalizedName();
	}

	public boolean isRunning()
	{
		return !this.isDisabled() && (this.getTurret(false) != null && this.wattsReceived >= this.getTurret(false).getFiringRequest());
	}

	public ItemStack hasAmmunition(ProjectileTypes ammunitionStack)
	{
		for (int i = 0; i < TPaoDaiZhan.UPGRADE_START_INDEX; i++)
		{
			ItemStack itemStack = this.containingItems[i];

			if (itemStack != null)
			{
				Item item = Item.itemsList[itemStack.itemID];

				if (item instanceof IAmmunition && ((IAmmunition) item).getType(itemStack.getItemDamage()).ordinal() == ammunitionStack.ordinal())
				{
					return itemStack;
				}
			}
		}

		return null;
	}

	public boolean useAmmunition(ItemStack ammoStack)
	{
		if (ammoStack != null)
		{
			if (ammoStack.getItemDamage() == AmmoType.BULLETINF.ordinal())
			{
				return true;
			}

			for (int i = 0; i < TPaoDaiZhan.UPGRADE_START_INDEX; i++)
			{
				ItemStack itemStack = this.containingItems[i];

				if (itemStack != null)
				{
					if (itemStack.isItemEqual(ammoStack))
					{
						itemStack.stackSize--;

						if (itemStack.stackSize <= 0)
						{
							itemStack = null;
						}

						this.setInventorySlotContents(i, itemStack);
						return true;
					}
				}
			}
		}
		return false;
	}

	/** Gets the change for the upgrade type 100% = 1.0 */
	public float getUpgradePercent(String name)
	{
		if (this.upgrades.containsKey(name))
		{
			return this.upgrades.get(name);
		}

		return 0;
	}

	@Override
	public void readFromNBT(NBTTagCompound nbt)
	{
		super.readFromNBT(nbt);
		this.wattsReceived = nbt.getDouble("wattsReceived");
		// Inventory
		NBTTagList var2 = nbt.getTagList("Items");
		this.containingItems = new ItemStack[this.getSizeInventory()];

		for (int var3 = 0; var3 < var2.tagCount(); ++var3)
		{
			NBTTagCompound var4 = (NBTTagCompound) var2.tagAt(var3);
			byte var5 = var4.getByte("Slot");

			if (var5 >= 0 && var5 < this.containingItems.length)
			{
				this.containingItems[var5] = ItemStack.loadItemStackFromNBT(var4);
			}
		}
	}

	@Override
	public void writeToNBT(NBTTagCompound nbt)
	{
		super.writeToNBT(nbt);
		nbt.setDouble("wattsReceived", this.wattsReceived);

		// Inventory
		NBTTagList itemTag = new NBTTagList();
		for (int slots = 0; slots < this.containingItems.length; ++slots)
		{
			if (this.containingItems[slots] != null)
			{
				NBTTagCompound itemNbtData = new NBTTagCompound();
				itemNbtData.setByte("Slot", (byte) slots);
				this.containingItems[slots].writeToNBT(itemNbtData);
				itemTag.appendTag(itemNbtData);
			}
		}

		nbt.setTag("Items", itemTag);
	}

	@Override
	public int getSizeInventory()
	{
		return this.containingItems.length;
	}

	/** Returns the stack in slot i */
	@Override
	public ItemStack getStackInSlot(int par1)
	{
		return this.containingItems[par1];
	}

	@Override
	public ItemStack getStackInSlotOnClosing(int par1)
	{
		if (this.containingItems[par1] != null)
		{
			ItemStack var2 = this.containingItems[par1];
			this.containingItems[par1] = null;
			return var2;
		}
		else
		{
			return null;
		}
	}

	@Override
	public ItemStack decrStackSize(int par1, int par2)
	{
		if (this.containingItems[par1] != null)
		{
			ItemStack var3;

			if (this.containingItems[par1].stackSize <= par2)
			{
				var3 = this.containingItems[par1];
				this.containingItems[par1] = null;
				return var3;
			}
			else
			{
				var3 = this.containingItems[par1].splitStack(par2);

				if (this.containingItems[par1].stackSize == 0)
				{
					this.containingItems[par1] = null;
				}

				return var3;
			}
		}
		else
		{
			return null;
		}
	}

	@Override
	public void setInventorySlotContents(int par1, ItemStack par2ItemStack)
	{
		this.containingItems[par1] = par2ItemStack;

		if (par2ItemStack != null && par2ItemStack.stackSize > this.getInventoryStackLimit())
		{
			par2ItemStack.stackSize = this.getInventoryStackLimit();
		}
	}

	@Override
	public int getInventoryStackLimit()
	{
		return 64;
	}

	@Override
	public boolean isUseableByPlayer(EntityPlayer par1EntityPlayer)
	{
		return true;
	}

	@Override
	public void openChest()
	{
	}

	@Override
	public void closeChest()
	{
	}

	@Override
	public boolean canConnect(ForgeDirection direction)
	{
		return true;
	}

	@Override
	public boolean isInvNameLocalized()
	{
		return true;
	}

	@Override
	public boolean isStackValidForSlot(int slotID, ItemStack itemStack)
	{
		if (slotID < UPGRADE_START_INDEX && itemStack.getItem() instanceof IAmmunition)
		{
			return true;
		}

		return false;
	}

	@Override
	public void onInventoryChanged()
	{
		super.onInventoryChanged();
		// this.processUpgrades();
	}

	@Override
	public String getChannel()
	{
		return ZhuYaoGangShao.CHANNEL;
	}
}